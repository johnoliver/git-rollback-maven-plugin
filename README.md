git-rollback-maven-plugin
=========================

Provides a release rollback functionality that deletes and git tags created by a maven release

Usage
=====

```
mvn release:prepare
...failure
mvn org.jclarity:git-rollback-maven-plugin:rollback
```

Alternatively to shorten the command, add the following to the pom.xml

```
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.jclarity</groupId>
          <artifactId>git-rollback-maven-plugin</artifactId>
          <version>1.0</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
```

Then execute with `mvn git-rollback:rollback`
