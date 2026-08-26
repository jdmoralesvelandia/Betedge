-- Public demo account, no sensitive data behind it. Password: Demo1234!
-- Hash generated with the exact BCryptPasswordEncoder this app uses (strength 10) and
-- independently verified to match before being committed here.
INSERT INTO users (email, password_hash, role)
VALUES ('demo@betedge.com', '$2a$10$GVGRc0j68EwEDqqX7GeNE.Y5uON.64A.kBieR6fbG282IEEogH7sG', 'USER')
ON CONFLICT (email) DO NOTHING;
