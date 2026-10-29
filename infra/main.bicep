// =============================================================================
// NEXORA - Infraestructura en Azure (Bicep)
//
// Crea, dentro de un grupo de recursos existente:
//   1. Log Analytics (registros de la aplicación, retención de 30 días y tope diario de 0,5 GB)
//   2. Entorno de Azure Container Apps (plan Consumo) conectado a Log Analytics
//   3. Servidor Azure SQL + base BD_NEXORA serverless con la OFERTA GRATUITA
//   4. Container App con la tienda (Tomcat 11): ingreso externo HTTPS, sondas a /salud, 0..1 réplicas
//
// Despliegue recomendado: bash infra/crear-infra.sh (genera las claves, muestra el what-if y pide confirmación).
// Despliegue manual:
//   az deployment group create -g rg-nexora -f infra/main.bicep \
//     -p sqlAdminLogin=nexoraadmin sqlAdminClave=<clave> appDbClave=<clave> tokenSalud=<token>
//
// Versiones de API (todas estables/GA, sin "-preview"):
//   - Microsoft.OperationalInsights/workspaces@2022-10-01  (versión estable ampliamente usada; admite listKeys())
//   - Microsoft.App/managedEnvironments y containerApps@2024-03-01 (GA; incluye sondas, secretos y reglas HTTP de escala)
//   - Microsoft.Sql/servers, firewallRules y databases@2023-08-01 (GA; incluye las propiedades de la oferta
//     gratuita useFreeLimit y freeLimitExhaustionBehavior). Si una suscripción rechazara esas dos propiedades con
//     esta versión, la alternativa documentada por Microsoft es 2023-08-01-preview solo para el recurso de la base.
// =============================================================================

targetScope = 'resourceGroup'

// ---------------------------------------------------------------- Parámetros

@description('Región de Azure de todos los recursos. Por defecto, la del grupo de recursos.')
param location string = resourceGroup().location

@description('Prefijo de los nombres de recursos (minúsculas y números).')
@minLength(2)
@maxLength(12)
param prefijo string = 'nexora'

@description('Sufijo que vuelve únicos los nombres globales (servidor SQL). Por defecto, derivado del grupo de recursos.')
@minLength(4)
@maxLength(13)
param sufijo string = uniqueString(resourceGroup().id)

@description('Imagen del contenedor. El valor inicial es una imagen pública de arranque rápido (escucha en el puerto 80 y no tiene /salud) para el primer despliegue; después se reemplaza por ghcr.io/<propietario>/nexora:<etiqueta>.')
param imagen string = 'mcr.microsoft.com/k8se/quickstart:latest'

@description('Servidor del registro privado de imágenes (por ejemplo ghcr.io). Vacío si la imagen es pública.')
param registroServidor string = ''

@description('Usuario del registro privado (para GHCR, el usuario de GitHub).')
param registroUsuario string = ''

@description('Clave o token de solo lectura del registro privado (para GHCR, un token con read:packages).')
@secure()
param registroClave string = ''

@description('Inicio de sesión del administrador del servidor SQL (no puede ser admin, sa, root, etc.).')
param sqlAdminLogin string

@description('Clave del administrador del servidor SQL.')
@secure()
param sqlAdminClave string

@description('Clave del usuario contenido nexora_app, con la que la aplicación se conecta a BD_NEXORA. Debe ser la misma que fija infra/inicializar-bd.sh.')
@secure()
param appDbClave string

@description('Token que autoriza GET /salud?bd=1 (cabecera X-Salud-Token) en producción; llega a la aplicación como NEXORA_TOKEN_SALUD. Vacío: la aplicación no lo define y /salud?bd=1 responde siempre 403 (las sondas de /salud no lo necesitan).')
@secure()
param tokenSalud string = ''

@description('Réplicas mínimas: 0 permite escalar a cero (más barato, con arranque en frío); 1 mantiene la aplicación siempre encendida. El máximo es siempre 1 porque las sesiones viven en memoria.')
@minValue(0)
@maxValue(1)
param minReplicas int = 0

@description('Versión que la aplicación informa en /salud y usa en ?v= de CSS/JS (sha corto del commit).')
param nexoraVersion string = 'dev'

@description('NEXORA_DEMO: muestra la cinta "Entorno de demostración" y las cuentas demo en el inicio de sesión.')
param modoDemo bool = true

@description('Usa la oferta gratuita de Azure SQL Database (una base por suscripción; al agotar el límite mensual se autopausa). Pon false si la suscripción ya la usó en otra base.')
param usarOfertaGratuita bool = true

// ---------------------------------------------------------------- Variables

var nombreLogs = 'log-${prefijo}-${sufijo}'
var nombreEntorno = 'cae-${prefijo}-${sufijo}'
var nombreSql = 'sql-${prefijo}-${sufijo}'
var nombreBd = 'BD_NEXORA'
var nombreApp = 'ca-${prefijo}'

// El registro se configura solo si llegan servidor, usuario y clave.
var usaRegistro = !empty(registroServidor) && !empty(registroUsuario) && !empty(registroClave)

// La imagen de arranque rápido escucha en el 80 y no responde en /salud: con ella se usa el puerto 80 y las
// sondas predeterminadas. Con la imagen real de NEXORA se usa el 8080 y las sondas a /salud.
var esImagenInicial = startsWith(imagen, 'mcr.microsoft.com/k8se/quickstart')
var puertoObjetivo = esImagenInicial ? 80 : 8080

// DB_URL contra Azure SQL: TLS obligatorio con validación del certificado del servidor.
var urlBd = 'jdbc:sqlserver://${servidorSql.properties.fullyQualifiedDomainName}:1433;database=${nombreBd};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;'

// El token de salud solo se publica (secreto + variable) si llega un valor: un secreto vacío no es válido.
var usaTokenSalud = !empty(tokenSalud)

// Secretos del Container App: la clave de la BD siempre; la del registro solo si hay registro; el token si existe.
var secretosBase = [
  {
    name: 'db-password'
    value: appDbClave
  }
]
var secretosRegistro = usaRegistro ? [
  {
    name: 'registry-password'
    value: registroClave
  }
] : []
var secretosSalud = usaTokenSalud ? [
  {
    name: 'salud-token'
    value: tokenSalud
  }
] : []
var variablesSalud = usaTokenSalud ? [
  {
    name: 'NEXORA_TOKEN_SALUD'
    secretRef: 'salud-token'
  }
] : []

// Sondas HTTP a /salud (no consulta la BD, así Azure SQL puede autopausarse). Tomcat con 0.5 vCPU puede tardar
// cerca de 2 minutos en arrancar: la sonda de arranque tolera hasta 30 + 10 x 15 = 180 segundos.
var sondas = [
  {
    type: 'Startup'
    httpGet: {
      path: '/salud'
      port: 8080
      scheme: 'HTTP'
    }
    initialDelaySeconds: 30
    periodSeconds: 15
    timeoutSeconds: 5
    failureThreshold: 10
  }
  {
    type: 'Liveness'
    httpGet: {
      path: '/salud'
      port: 8080
      scheme: 'HTTP'
    }
    initialDelaySeconds: 10
    periodSeconds: 30
    timeoutSeconds: 5
    failureThreshold: 3
  }
  {
    type: 'Readiness'
    httpGet: {
      path: '/salud'
      port: 8080
      scheme: 'HTTP'
    }
    initialDelaySeconds: 10
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 3
    successThreshold: 1
  }
]

// ---------------------------------------------------------------- Log Analytics

resource logs 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: nombreLogs
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
    // Tope diario de ingesta: un pico de registros no consume el crédito. Al alcanzarlo se dejan de guardar
    // registros hasta el día siguiente (la aplicación sigue funcionando).
    workspaceCapping: {
      dailyQuotaGb: json('0.5')
    }
  }
}

// ---------------------------------------------------------------- Entorno de Container Apps (plan Consumo)

resource entorno 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: nombreEntorno
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
    zoneRedundant: false
  }
}

// ---------------------------------------------------------------- Azure SQL

resource servidorSql 'Microsoft.Sql/servers@2023-08-01' = {
  name: nombreSql
  location: location
  properties: {
    administratorLogin: sqlAdminLogin
    administratorLoginPassword: sqlAdminClave
    minimalTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
    version: '12.0'
  }
}

// 0.0.0.0 - 0.0.0.0 es la regla especial "Permitir que los servicios de Azure accedan al servidor":
// deja entrar al Container App (que no tiene IP fija). El acceso sigue exigiendo usuario y clave.
resource reglaServiciosAzure 'Microsoft.Sql/servers/firewallRules@2023-08-01' = {
  parent: servidorSql
  name: 'AllowAllWindowsAzureIps'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// Base serverless de uso general (Gen5, máximo 1 vCore) con la oferta gratuita: al agotar el límite mensual
// gratuito la base se autopausa (no cobra) hasta el mes siguiente. 32 GB es el tamaño máximo que cubre la oferta.
resource baseDatos 'Microsoft.Sql/servers/databases@2023-08-01' = {
  parent: servidorSql
  name: nombreBd
  location: location
  sku: {
    name: 'GP_S_Gen5_1'
    tier: 'GeneralPurpose'
    family: 'Gen5'
    capacity: 1
  }
  properties: {
    collation: 'Modern_Spanish_CI_AS'
    maxSizeBytes: 34359738368
    minCapacity: json('0.5')
    autoPauseDelay: 60
    zoneRedundant: false
    requestedBackupStorageRedundancy: 'Local'
    useFreeLimit: usarOfertaGratuita
    freeLimitExhaustionBehavior: usarOfertaGratuita ? 'AutoPause' : null
  }
}

// ---------------------------------------------------------------- Container App

resource appNexora 'Microsoft.App/containerApps@2024-03-01' = {
  name: nombreApp
  location: location
  properties: {
    managedEnvironmentId: entorno.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: puertoObjetivo
        transport: 'auto'
        allowInsecure: false
        traffic: [
          {
            latestRevision: true
            weight: 100
          }
        ]
      }
      registries: usaRegistro ? [
        {
          server: registroServidor
          username: registroUsuario
          passwordSecretRef: 'registry-password'
        }
      ] : []
      secrets: concat(secretosBase, secretosRegistro, secretosSalud)
    }
    template: {
      containers: [
        {
          name: 'nexora'
          image: imagen
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: concat([
            {
              name: 'NEXORA_ENTORNO'
              value: 'produccion'
            }
            {
              name: 'NEXORA_DEMO'
              value: modoDemo ? 'true' : 'false'
            }
            {
              name: 'NEXORA_VERSION'
              value: nexoraVersion
            }
            {
              name: 'DB_URL'
              value: urlBd
            }
            {
              name: 'DB_USER'
              value: 'nexora_app'
            }
            {
              name: 'DB_PASSWORD'
              secretRef: 'db-password'
            }
          ], variablesSalud)
          probes: esImagenInicial ? [] : sondas
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: 1
        rules: [
          {
            name: 'http'
            http: {
              metadata: {
                concurrentRequests: '50'
              }
            }
          }
        ]
      }
    }
  }
  dependsOn: [
    baseDatos
  ]
}

// ---------------------------------------------------------------- Salidas

@description('Dirección pública (HTTPS) de la tienda.')
output urlApp string = 'https://${appNexora.properties.configuration.ingress.fqdn}'

@description('Nombre DNS del servidor Azure SQL (para sqlcmd y SSMS).')
output fqdnServidorSql string = servidorSql.properties.fullyQualifiedDomainName

@description('Nombre del servidor Azure SQL.')
output nombreServidorSql string = servidorSql.name

@description('Nombre de la base de datos.')
output nombreBaseDatos string = baseDatos.name

@description('Nombre del Container App.')
output nombreContainerApp string = appNexora.name

@description('Nombre del entorno de Container Apps.')
output nombreEntornoApps string = entorno.name

@description('Nombre del área de trabajo de Log Analytics.')
output nombreLogAnalytics string = logs.name
