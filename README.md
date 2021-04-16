# S.E.C.
*Sistemas de Elevada Confiabilidade - IST - 2020/2021*

---

<br/>

First, there must be a local *mysql* database named 'sec' which contains a user named 'user' and with 'password' as its password.

To do so, please refer to these commands:

```mysql
CREATE USER 'user'@'localhost' IDENTIFIED BY 'pass';
GRANT ALL PRIVILEGES ON * . * TO 'user'@'localhost';
```
```mysql
CREATE DATABASE sec;
```

<br/><br/>

Before running the clients and the server, the environment (a set of grids per epoch), and the key-pairs of each entity must be generated.

The bellow commands must be run in a terminal inside the root directory of the project (```/sec```).

<br/>

---

<br/>

**Note:** 
- 'userCount' must match the intended amount of running users;
- in order to run the tests a server must also be running;

<br/>


To run the **EnvironmentGenerator**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="[nX] [nY] [epochCount] [userCount]" -Dstart-class=pt.tecnico.sec.EnvironmentGenerator
```
***Suggestion:** 3x3 grid with 3 users (the amount of epochs is not that relevant)*

<br/>


To run the **RSAKeyGenerator**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="[userCount]" -Dstart-class=pt.tecnico.sec.RSAKeyGenerator
```
***Suggestion:** 3 users*

<br/>


To run the **ServerApplication**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.server.ServerApplication
```

<br/>


To run the **ClientApplication**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="[userId]" -Dstart-class=pt.tecnico.sec.client.ClientApplication
```
***Suggestion:** 3 users using userIds 0, 1 and 2*

<br/>


To run the **HealthAuthorityApplication**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.healthauthority.HealthAuthorityApplication
```

<br/>


To run the tests:
```bash

```

<br/>

