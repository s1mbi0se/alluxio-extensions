## Backblaze B2 Under Storage

Alluxio under storage implementation for accessing Backblaze B2 using the `b2://` scheme.

### Build

```bash
mvn package
```

### Mount

```bash
./bin/alluxio fs mount --option b2AccessKeyId=<access-key> --option b2SecretKey=<secret-key>\
  /mnt/b2 b2://<B2_BUCKET>/<B2_DIRECTORY>
```
