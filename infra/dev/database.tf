resource "aws_db_subnet_group" "database" {
  name       = "team2-dev"
  subnet_ids = aws_subnet.database[*].id
}

resource "aws_db_instance" "database" {
  identifier                  = "team2-dev"
  engine                      = "postgres"
  engine_version              = var.db_engine_version
  instance_class              = var.db_instance_class
  allocated_storage           = var.db_allocated_storage
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = "team2"
  username                    = "team2_admin"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.database.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  availability_zone           = data.aws_availability_zones.available.names[0]
  publicly_accessible         = false
  multi_az                    = false
  backup_retention_period     = 7
  backup_window               = "18:00-19:00"
  maintenance_window          = "sat:19:00-sat:20:00"
  auto_minor_version_upgrade  = false
  allow_major_version_upgrade = false
  apply_immediately           = false
  deletion_protection         = true
  skip_final_snapshot         = false
  final_snapshot_identifier   = "team2-dev-final-snapshot"
  copy_tags_to_snapshot       = true
}

resource "aws_secretsmanager_secret_rotation" "database" {
  secret_id        = aws_db_instance.database.master_user_secret[0].secret_arn
  rotation_enabled = false
}
