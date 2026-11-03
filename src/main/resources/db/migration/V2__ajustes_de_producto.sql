-- Producto inhabilitado por local: no se puede vender en ese local (el ingreso sigue
-- permitido). Lo cambia el administrador desde "Invalidar producto" en la app.
ALTER TABLE rb_stock ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

-- Registro de los ajustes manuales del administrador: correccion del stock contado
-- (AJUSTE_STOCK) y habilitar/inhabilitar el producto en el local (ESTADO_PRODUCTO).
-- El id es el que genero el celular (idempotente igual que el resto de /sync).
CREATE TABLE rb_ajustes (
    id                UUID         PRIMARY KEY,
    tipo              VARCHAR(20)  NOT NULL,
    local_id          UUID         NOT NULL REFERENCES rb_locales (id),
    product_id        UUID         NOT NULL REFERENCES rb_products (id),
    device_id         UUID         NOT NULL REFERENCES rb_devices (id),
    user_email        VARCHAR(255) NOT NULL,
    cantidad_anterior INT,
    cantidad_nueva    INT,
    activo            BOOLEAN,
    created_at        TIMESTAMPTZ  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX rb_ajustes_local_product_idx ON rb_ajustes (local_id, product_id);
