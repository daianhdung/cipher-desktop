# Runtime dependencies

- Xerial SQLite JDBC 3.46.1.0: https://github.com/xerial/sqlite-jdbc — Apache 2.0 / BSD notices, includes SQLite native libraries.
- SLF4J API + NOP 2.0.13: https://www.slf4j.org — MIT license. NOP avoids a separate logging stack.

Downloaded from Maven Central. Exact SHA-256 digests are pinned in ../scripts/build.py.
License texts extracted from the distributed JARs are in licenses/ and remain inside
the original JARs. These dependencies are runtime-only; application code uses java.sql.
No DB process, Spring or cloud service is required.
