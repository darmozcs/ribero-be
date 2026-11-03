-- Producto eliminado de un local: deja de aparecer en el catalogo de ese local y su stock
-- queda en 0. Los otros locales lo siguen viendo. Un ingreso posterior en el local lo
-- vuelve a agregar. Lo hace vendedor o administrador desde "Eliminar producto".
ALTER TABLE rb_stock ADD COLUMN eliminado BOOLEAN NOT NULL DEFAULT FALSE;

-- "Editar producto" tambien queda registrado en rb_ajustes (tipo EDITAR_PRODUCTO) con el
-- nombre y precio nuevos; ELIMINAR_PRODUCTO guarda el stock que tenia.
ALTER TABLE rb_ajustes ADD COLUMN nombre VARCHAR(200);
ALTER TABLE rb_ajustes ADD COLUMN precio NUMERIC(12,2);
