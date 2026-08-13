ALTER TABLE student_responsibles
ADD COLUMN is_admin BOOLEAN DEFAULT FALSE;
--so pode ter 1 desse, ai se o mano for excluir a conta apaga a criança junto ent se outro apagar mas nao for esse da nada pra criança etc, acho bom criar uma função pra passar esse admin e tbm so ele pode adicionar mais responsaveis ff boloris viado