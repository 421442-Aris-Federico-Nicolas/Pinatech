INSERT INTO categories (name, slug, is_active) VALUES
('Procesadores', 'procesadores', TRUE), ('Placas de video', 'placas-de-video', TRUE), ('Memorias RAM', 'memorias-ram', TRUE), ('Almacenamiento', 'almacenamiento', TRUE), ('Periféricos', 'perifericos', TRUE)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO brands (name, is_active) VALUES ('AMD', TRUE), ('Intel', TRUE), ('NVIDIA', TRUE), ('Kingston', TRUE), ('Logitech', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (name, slug, description, price, category_id, brand_id, is_active) VALUES
('AMD Ryzen 7 7800X3D', 'amd-ryzen-7-7800x3d', 'Procesador AM5 para gaming de alto rendimiento.', 549999.00, (SELECT id FROM categories WHERE slug = 'procesadores'), (SELECT id FROM brands WHERE name = 'AMD'), TRUE),
('Intel Core i5-14600K', 'intel-core-i5-14600k', 'Procesador Intel de 14ª generación.', 419999.00, (SELECT id FROM categories WHERE slug = 'procesadores'), (SELECT id FROM brands WHERE name = 'Intel'), TRUE),
('NVIDIA GeForce RTX 4070', 'nvidia-geforce-rtx-4070', 'Placa de video con 12 GB de VRAM.', 899999.00, (SELECT id FROM categories WHERE slug = 'placas-de-video'), (SELECT id FROM brands WHERE name = 'NVIDIA'), TRUE),
('Kingston Fury 32 GB DDR5', 'kingston-fury-32gb-ddr5', 'Kit de memoria DDR5 de alto rendimiento.', 189999.00, (SELECT id FROM categories WHERE slug = 'memorias-ram'), (SELECT id FROM brands WHERE name = 'Kingston'), TRUE),
('Logitech G Pro X', 'logitech-g-pro-x', 'Auriculares gaming con micrófono desmontable.', 159999.00, (SELECT id FROM categories WHERE slug = 'perifericos'), (SELECT id FROM brands WHERE name = 'Logitech'), TRUE)
ON CONFLICT (slug) DO NOTHING;
