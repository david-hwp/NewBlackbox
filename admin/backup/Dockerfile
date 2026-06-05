FROM mysql:8.0

COPY mysql-backup.sh /usr/local/bin/mysql-backup.sh

ENTRYPOINT ["sh", "/usr/local/bin/mysql-backup.sh"]
