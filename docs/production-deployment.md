# Despliegue productivo en VPS

Este runbook establece una base segura para un unico VPS. No convierte al sistema en
production-ready por si solo: cada release debe completar
[`production-checklist.md`](production-checklist.md), pruebas de carga, pruebas de
restauracion y una revision de riesgos.

## 1. Dominio y DNS

1. Reservar un dominio exclusivo, por ejemplo `store.example.com`.
2. Crear un registro `A` hacia la IPv4 publica del VPS. Crear `AAAA` solo si IPv6 esta
   configurado y protegido por el firewall; de lo contrario eliminarlo.
3. Usar un TTL corto durante el primer despliegue y confirmar la resolucion desde mas de
   un resolvedor antes de iniciar Caddy.
4. No poner otro proxy/CDN delante de Caddy sin configurar explicitamente sus rangos de
   proxy confiables. Caddy descarta por defecto encabezados `X-Forwarded-*` enviados por
   clientes y genera la cadena confiable hacia Nginx.
5. Mantener TCP 80 y 443 accesibles para redireccion y ACME. UDP 443 permite HTTP/3 y se
   puede omitir si la politica de red no lo admite.

## 2. Preparar y proteger el VPS

Usar una distribucion soportada, aplicar actualizaciones de seguridad y sincronizar la
hora. Crear un usuario operador sin privilegios permanentes y agregar su clave publica
antes de endurecer SSH. Mantener abierta una segunda sesion mientras se prueba el cambio.

En `/etc/ssh/sshd_config` o un archivo de `sshd_config.d`:

```text
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
```

Validar con `sudo sshd -t`, recargar SSH y comprobar un nuevo acceso con clave antes de
cerrar la sesion existente. Restringir SSH a las IP de administracion cuando sea posible.
Un ejemplo con UFW, ajustando primero la regla SSH al puerto y origen reales, es:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
sudo ufw status verbose
```

PostgreSQL, backend y frontend no publican puertos del host. No agregar reglas Docker o
firewall para 5432, 8080 ni el puerto interno 80 de Nginx. Instalar Docker Engine y el
plugin Compose desde el repositorio oficial de Docker, no mediante scripts remotos sin
revision. El acceso al socket Docker equivale a root: limitar el grupo `docker`, activar
MFA en el registry y usar un token de solo lectura para pulls.

La red `edge` reserva `172.31.250.0/28` y Nginx confia unicamente en la IP de Caddy
`172.31.250.2`. Comprobar que ese rango no colisiona con VPN/VPC/redes Docker existentes
antes del despliegue. Si colisiona, cambiar coordinadamente la subred, las dos IP de
`docker-compose.prod.yml` y `set_real_ip_from` en la imagen frontend, reconstruirla y
volver a validarla; no ampliar la confianza a todos los rangos privados.

`app` y `data` son redes internas. Backend usa ademas una red `egress` exclusiva porque
debe consultar la API HTTPS de Mercado Pago; esa red no publica puertos ni permite entrada
desde Caddy. Si se aplica filtrado de salida en el host, autorizar DNS/NTP y los endpoints
HTTPS necesarios, probar pagos/refunds y mantener la lista de destinos revisada.

## 3. Artefactos y secretos

Clonar o copiar solo una revision aprobada a `/opt/pinatech`. Para cada imagen, completar
el repositorio `*_IMAGE` y su `*_IMAGE_DIGEST`; Compose los une de esta forma y no admite
omitir el digest:

```text
registry.example.com/pinatech/backend@sha256:<64-hex>
```

CI debe construir y escanear una vez. Staging y produccion deben consumir exactamente el
mismo digest. Obtener el digest publicado desde el registry con `docker buildx imagetools
inspect IMAGE:TAG` y verificar su firma/procedencia si el registry lo soporta.
Backend/frontend deben provenir de los Dockerfiles de esta revision. La configuracion fue
probada con Caddy `2.10.2-alpine` (UID 1000) y PostgreSQL `17.10-alpine` (UID 70); al
actualizarlos, verificar UID, healthcheck, directorios escribibles y controles read-only en
staging antes de cambiar el digest productivo.

Crear el archivo privado fuera del repositorio:

```bash
sudo install -d -o root -g root -m 0700 /etc/pinatech
sudo install -o root -g root -m 0600 \
  /opt/pinatech/.env.production.example /etc/pinatech/production.env
sudoedit /etc/pinatech/production.env
sudo stat -c '%U:%G %a %n' /etc/pinatech/production.env
```

Generar valores independientes, por ejemplo `openssl rand -hex 32` para la clave de base
de datos y `openssl rand -hex 48` para JWT. No pegarlos en tickets, chat, historial de
shell, logs ni CI. `docker compose config` sin `--quiet` muestra variables interpoladas;
no guardar ni compartir esa salida. Los secretos de entorno tambien son visibles para
usuarios con privilegios Docker, por lo que el acceso al VPS y al daemon debe ser minimo.

Autenticarse al registry sin poner el token en la linea de comandos:

```bash
printf '%s' "$REGISTRY_TOKEN" | sudo docker login registry.example.com \
  --username "$REGISTRY_USER" --password-stdin
unset REGISTRY_TOKEN
```

## 4. Validar y desplegar por digest

Definir `APP_DOMAIN` sin esquema ni ruta. Si se habilita Resend, definir un unico
destinatario valido en `SELLER_NOTIFICATION_EMAIL`; recibe una notificacion al crear el
pedido y otra al aprobar el pago. Definir tambien
`EMAIL_LOGO_URL` como una URL HTTPS publica y absoluta, con host y sin credenciales,
query ni fragmento. Esta URL es independiente de los enlaces de cuenta y el cliente de
correo carga la imagen publica directamente, sin un adjunto CID. Antes de habilitar
Resend, comprobar que responde `200` con un tipo de contenido de imagen. Con
`MP_ENABLED=false`, dejar vacias las credenciales MP. Validar sin imprimir la
configuracion resuelta:

```bash
cd /opt/pinatech
sudo docker compose --env-file /etc/pinatech/production.env \
  -f docker-compose.prod.yml config --quiet
sudo docker compose --env-file /etc/pinatech/production.env \
  -f docker-compose.prod.yml pull
```

Antes del cambio tomar un backup consistente y confirmado segun la seccion de backups.
Flyway no tiene migraciones descendentes: revisar cada migracion y ensayarla en staging
contra una restauracion representativa. En produccion, el backend ejecuta Flyway al
arrancar y solo queda healthy despues de validar el esquema y la base de datos:

```bash
sudo docker compose --env-file /etc/pinatech/production.env \
  -f docker-compose.prod.yml up -d postgres
sudo docker compose --env-file /etc/pinatech/production.env \
  -f docker-compose.prod.yml up -d backend
sudo docker compose --env-file /etc/pinatech/production.env \
  -f docker-compose.prod.yml logs --since=10m backend
sudo docker compose --env-file /etc/pinatech/production.env \
  -f docker-compose.prod.yml up -d --wait
```

No ejecutar dos backends de versiones incompatibles durante una migracion destructiva.
Preferir cambios de esquema expand/contract compatibles. Los cambios aditivos pueden desplegar
el backend primero. Si se elimina o cambia un contrato consumido por el frontend, desplegar
primero una version compatible del frontend y mantener el contrato deprecado durante una ventana
de compatibilidad acotada. El aviso de actualizacion ayuda a renovar pestañas abiertas, pero no
demuestra que todos los clientes antiguos hayan desaparecido. Retirar el contrato backend en una
release posterior, despues de esa ventana y de revisar su uso del lado servidor. Confirmar desde
una red externa:

```bash
curl --fail --silent --show-error --location http://store.example.com/api/health
curl --fail --silent --show-error https://store.example.com/api/health
curl --fail --silent --show-error https://store.example.com/version.json
```

Revisar certificado, redireccion, HSTS, pagina Angular, login, lectura/escritura autorizada
y carga/descarga de archivos. `index.html` y `version.json` deben responder con `no-store`, los
bundles JS/CSS con hash deben ser inmutables y un chunk inexistente debe responder `404` sin HTML.
`docker compose ps` debe mostrar todos los servicios healthy.

## 5. Mercado Pago en produccion

Mantener `MP_ENABLED=false` hasta completar pruebas sandbox y aprobacion comercial. Para
activar produccion:

1. Usar una aplicacion Mercado Pago propiedad de la cuenta comercial correcta y habilitar
   credenciales de produccion.
2. Confirmar que `MP_COLLECTOR_ID` es el seller/collector esperado y que la moneda, cuenta
   bancaria, identidad fiscal y permisos corresponden al comercio.
3. Configurar `MP_ENVIRONMENT=production`, `MP_ACCESS_TOKEN` con el token productivo y
   `MP_WEBHOOK_SECRET` con un Webhooks secret nuevo. No se usa public key ni SDK de Mercado
   Pago en Angular. Establecer `MP_PRODUCTION_CONFIRMATION=true` como aprobacion explicita;
   el backend rechaza el arranque productivo sin esta confirmacion o con un token que no sea
   `APP_USR-`.
4. Registrar para eventos de pago exactamente esta URL, sin slash final:

```text
https://store.example.com/api/payments/webhooks/mercado-pago
```

5. Probar firma, notificacion duplicada, rechazo, expiracion, aprobacion tardia y refund.
   Ajustar `MP_RECONCILIATION_LOOKBACK` al periodo operativo aprobado; por defecto se buscan
   pagos omitidos hasta 30 dias despues del vencimiento de la preferencia.
6. Habilitar `MP_ENABLED=true` durante una ventana controlada, reiniciar backend y verificar
   `GET /api/checkout/capabilities`, logs, una operacion real de importe minimo y la
   conciliacion en el panel del proveedor.

Las URLs de retorno del navegador no prueban un pago. Solo el webhook firmado seguido por
la consulta autoritativa al proveedor puede aprobarlo.

## 6. Backups, PITR y restauracion

Un volumen Docker, un snapshot sin consistencia o un `pg_dump` local no constituyen por si
solos una estrategia de recuperacion. Definir RPO, RTO, retencion e inmutabilidad antes del
lanzamiento.

- PostgreSQL: usar una herramienta mantenida que haga base backups consistentes y archive
  WAL para PITR, con cifrado autenticado y verificacion de integridad. Enviar el repositorio
  a otra cuenta/proveedor o region; no dejar la unica copia en el VPS.
- Archivos: respaldar el volumen `media_uploads` con una herramienta cifrada, incremental y
  autenticada hacia almacenamiento offsite versionado/inmutable. Coordinar el punto de
  restauracion de archivos con el de PostgreSQL.
- Caddy: preservar `caddy_data` en una recuperacion del host cuando sea posible, pero no
  mezclar sus claves TLS con backups de datos de negocio ni asumir que reemplaza ACME.
- Accesos: guardar credenciales de backup fuera del repositorio y separadas de las de
  produccion; aplicar minimo privilegio y alertar ante borrados o fallos.

Al menos trimestralmente, restaurar en una red aislada sin salida hacia Mercado Pago ni
correo: recuperar PostgreSQL a un timestamp elegido, restaurar archivos, arrancar el mismo
digest, verificar checksums/migraciones y ejecutar pruebas funcionales. Registrar duracion,
RPO/RTO observado y evidencia. Un backup no se considera valido hasta superar este ensayo.

## 7. Observabilidad y alertas

Enviar logs estructurados offsite con acceso restringido, retencion y redaccion; no activar
logging de credenciales. Monitorizar externamente HTTPS y expiracion TLS. Recoger al menos:

- estado/restarts/OOM y uso de CPU, memoria, disco, inodos y volumenes;
- latencia y tasas HTTP 4xx/5xx en Caddy, y salud readiness/liveness del backend;
- conexiones, locks, almacenamiento, WAL y exito/antiguedad de backups PostgreSQL;
- fallos de login, creacion de orden, webhook, consulta Mercado Pago, refund y conciliacion;
- cola o cantidad de ordenes `REFUND_PENDING` y reservas que no expiran.

Alertar con responsables y escalamiento definidos por indisponibilidad, errores sostenidos,
disco mayor al umbral, certificado proximo a vencer, backup/PITR atrasado, migracion fallida,
webhooks rechazados y refunds pendientes. Probar los canales de alerta.

## 8. Rollback

Conservar los digests y el archivo de configuracion de la ultima version sana. Si no hubo
cambio de esquema incompatible, restaurar `BACKEND_IMAGE_DIGEST` y
`FRONTEND_IMAGE_DIGEST` anteriores, validar con `config --quiet`, hacer `pull` y
`up -d --wait`. Nunca usar un tag movible para simular rollback.

Si una migracion cambio datos o esquema de forma incompatible, detener escrituras y seguir
el plan aprobado para esa migracion. Puede requerir corregir hacia adelante o restaurar
PostgreSQL y `media_uploads` al mismo punto, aceptando el RPO. No ejecutar SQL de rollback
improvisado. Verificar checkout y pagos/refunds pendientes antes de reabrir trafico.

## 9. Rotacion e incidentes

Rotar periodicamente y ante sospecha de exposicion:

- JWT: reemplazar `JWT_SECRET` y reiniciar backend; todas las sesiones existentes quedan
  invalidadas.
- PostgreSQL: crear/rotar la credencial con una transicion coordinada, actualizar el archivo
  privado y reiniciar; comprobar conexiones antes de revocar la anterior.
- Mercado Pago: emitir/revocar el access token segun el procedimiento del proveedor y rotar
  Webhooks secret coordinando dashboard y backend para no perder notificaciones.
- Registry, SSH y backups: emitir credenciales nuevas de minimo privilegio, probarlas y
  revocar las anteriores; retirar claves de ex integrantes inmediatamente.

Ante un incidente: declarar responsable y cronologia, preservar logs/evidencia, aislar sin
destruir datos, deshabilitar pagos si su integridad es dudosa, revocar secretos afectados,
consultar Mercado Pago por el estado autoritativo, recuperar desde artefactos/datos
verificados y comunicar segun obligaciones legales. Luego documentar causa raiz, alcance,
ordenes afectadas y acciones preventivas; un secreto filtrado debe rotarse aunque se borre
del historial Git.
