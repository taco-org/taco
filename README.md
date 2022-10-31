# SheetAnalyzer

SheetAnalyzer is a library for analyzing the depenency and formula structure of a spreadsheet

## API

[SheetAnalyzer.java](https://github.com/dataspread/sheetanalyzer/blob/main/src/main/java/org/dataspread/sheetanalyzer/SheetAnalyzer.java) provides the API for use

## Installation

1. Configure maven to retrieve from the Central Repository: (https://central.sonatype.org/consume/consume-apache-maven/)
2. Add to your project's `pom.xml`:

```xml
<dependency>
  <groupId>io.github.dataspread</groupId>
  <artifactId>sheetanalyzer</artifactId>
  <version>0.0.2</version>
</dependency>
```

## Test

```shell
mvn test
```

## Deployment

```shell
mvn versions:set -DnewVersion=1.2.3
```

```shell
mvn clean deploy -P release
```

More information for deploying:

- https://central.sonatype.org/publish/release/
- https://central.sonatype.org/publish/publish-maven/
