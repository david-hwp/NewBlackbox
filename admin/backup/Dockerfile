FROM alpine:3.20

RUN apk add --no-cache mariadb-client gzip tzdata

COPY mysql-backup.sh /usr/local/bin/mysql-backup.sh

ENTRYPOINT ["sh", "/usr/local/bin/mysql-backup.sh"]
