-- Esquema inicial de ribero-be. Comparte la base "nexora" con darmoz-auth y el resto de los
-- servicios, por eso todas las tablas llevan el prefijo rb_.
--
-- Los usuarios se identifican por email (no por el user_id de darmoz-auth, que puede
-- reemitirse si la cuenta se borra y se vuelve a crear).
--
-- Las operaciones (ingresos, ventas, anulaciones, cierres) se generan en el celular, muchas
-- veces sin conexion, y llegan despues por POST /api/rb/sync. Su id es el UUID que genera el
-- celular: reenviar la misma operacion choca contra la PK y no se duplica.

CREATE TABLE rb_users (
    user_id      UUID         PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    roles        VARCHAR(500) NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE rb_locales (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre     VARCHAR(120) NOT NULL,
    direccion  VARCHAR(255),
    activo     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Locales a los que puede entrar cada vendedor. El administrador (APP_ADMIN) entra a todos.
CREATE TABLE rb_user_locales (
    email    VARCHAR(255) NOT NULL,
    local_id UUID         NOT NULL REFERENCES rb_locales (id) ON DELETE CASCADE,
    PRIMARY KEY (email, local_id)
);

-- Cada celular es una caja: numera sus propias ventas (<code>-<nro>) y hace su cierre.
CREATE SEQUENCE rb_devices_code_seq;
CREATE TABLE rb_devices (
    id         UUID         PRIMARY KEY,
    code       VARCHAR(10)  NOT NULL UNIQUE,
    nombre     VARCHAR(120),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Catalogo global por codigo de barras; el stock es por local.
-- precio_at es la fecha (del celular) del dato de nombre/precio vigente: un ingreso hecho
-- offline que llega tarde no pisa un precio mas nuevo.
CREATE TABLE rb_products (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    barcode    VARCHAR(64)   NOT NULL UNIQUE,
    format     VARCHAR(32),
    nombre     VARCHAR(200)  NOT NULL,
    precio     NUMERIC(12,2) NOT NULL,
    precio_at  TIMESTAMPTZ   NOT NULL,
    has_photo  BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE rb_product_photos (
    product_id UUID        PRIMARY KEY REFERENCES rb_products (id) ON DELETE CASCADE,
    jpeg       BYTEA       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Puede quedar negativo: una venta hecha sin conexion no se rechaza por falta de stock.
CREATE TABLE rb_stock (
    local_id   UUID        NOT NULL REFERENCES rb_locales (id),
    product_id UUID        NOT NULL REFERENCES rb_products (id),
    cantidad   INT         NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (local_id, product_id)
);

CREATE TABLE rb_ingresos (
    id          UUID         PRIMARY KEY,
    local_id    UUID         NOT NULL REFERENCES rb_locales (id),
    device_id   UUID         NOT NULL REFERENCES rb_devices (id),
    user_email  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE rb_ingreso_items (
    ingreso_id UUID NOT NULL REFERENCES rb_ingresos (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES rb_products (id),
    cantidad   INT  NOT NULL CHECK (cantidad > 0),
    PRIMARY KEY (ingreso_id, product_id)
);

CREATE TABLE rb_ventas (
    id          UUID          PRIMARY KEY,
    local_id    UUID          NOT NULL REFERENCES rb_locales (id),
    device_id   UUID          NOT NULL REFERENCES rb_devices (id),
    numero      VARCHAR(20)   NOT NULL UNIQUE,
    user_email  VARCHAR(255)  NOT NULL,
    forma_pago  VARCHAR(20)   NOT NULL,
    total       NUMERIC(12,2) NOT NULL,
    anulada     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL,
    received_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX rb_ventas_local_created_idx ON rb_ventas (local_id, created_at);

-- Nombre y precio se copian al momento de la venta: el ticket no cambia si despues cambia
-- el catalogo.
CREATE TABLE rb_venta_items (
    venta_id    UUID          NOT NULL REFERENCES rb_ventas (id) ON DELETE CASCADE,
    linea       INT           NOT NULL,
    product_id  UUID          NOT NULL REFERENCES rb_products (id),
    barcode     VARCHAR(64)   NOT NULL,
    nombre      VARCHAR(200)  NOT NULL,
    precio_unit NUMERIC(12,2) NOT NULL,
    cantidad    INT           NOT NULL CHECK (cantidad > 0),
    PRIMARY KEY (venta_id, linea)
);

CREATE TABLE rb_anulaciones (
    id          UUID         PRIMARY KEY,
    venta_id    UUID         NOT NULL UNIQUE REFERENCES rb_ventas (id),
    device_id   UUID         NOT NULL REFERENCES rb_devices (id),
    user_email  VARCHAR(255) NOT NULL,
    motivo      VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Los totales los calcula el celular sobre sus propias ventas desde su ultimo cierre.
CREATE TABLE rb_cierres (
    id                UUID          PRIMARY KEY,
    local_id          UUID          NOT NULL REFERENCES rb_locales (id),
    device_id         UUID          NOT NULL REFERENCES rb_devices (id),
    user_email        VARCHAR(255)  NOT NULL,
    desde             TIMESTAMPTZ,
    hasta             TIMESTAMPTZ   NOT NULL,
    cantidad_ventas   INT           NOT NULL,
    total_ventas      NUMERIC(12,2) NOT NULL,
    cantidad_anuladas INT           NOT NULL,
    total_anulado     NUMERIC(12,2) NOT NULL,
    neto              NUMERIC(12,2) NOT NULL,
    por_forma_pago    JSONB         NOT NULL,
    received_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
