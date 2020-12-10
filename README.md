# SingleInstanceLock
Prevent your application from starting more than once on the same machine


---


[![License](http://img.shields.io/badge/License-Apache_v2.0-802879.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Java Version](http://img.shields.io/badge/Java-1.6%2B-2E6CB8.svg)](https://java.com)
[![Apache Maven ready](http://img.shields.io/badge/Apache_Maven_ready-3.3.9%2B-FF6804.svg)](https://maven.apache.org/)


---






##Example

```Java

	SingleInstanceLock.setApplicationId("some_unique_application_id");

    boolean locked = SingleInstanceLock.lock();
    
    if (locked) {
    	//Lock acquired
    } else {
    	//Other application instance has lock
    }
    
    //...

    SingleInstanceLock.release();
    
```



---


<img src="http://maven.apache.org/images/maven-logo-black-on-white.png" alt="Built with Maven" width="150">

This project can be built with Maven

Maven command:
```
$ mvn clean install
```

pom.xml entry in your project:
```
<dependency>
	<groupId>ch.thn.app</groupId>
	<artifactId>singleinstancelock</artifactId>
	<version>0.0.1-SNAPSHOT</version>
</dependency>
```

---

