# Create a new Release

## Prepare the release 

Update the pom.xml with the  required version number:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.snapgames</groupId>
  <artifactId>marknote</artifactId>
  <version>{{My_RELEASE_VERSION}}</version>
  <packaging>jar</packaging>
  ...
</project>
```
## Build native packages

Go to the Github Actions tab

<img width="1874" height="725" alt="release-workflow-step-1" src="https://github.com/user-attachments/assets/73e2a4dd-faee-451b-b760-72649b38ac2a" />

<img width="475" height="450" alt="release-workflow-step-2" src="https://github.com/user-attachments/assets/1ccf5308-a239-4b7c-bbbe-b94d73959b40" />

<img width="392" height="178" alt="release-workflow-step-3" src="https://github.com/user-attachments/assets/ac6fd1a2-1fd8-4b94-8793-0e9aa51e4783" />

<img width="1241" height="549" alt="release-workflow-step-4" src="https://github.com/user-attachments/assets/fac41a89-350f-496f-8f97-ad432d9a18de" />

And carefully wait until release has been packaged.
