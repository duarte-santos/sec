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

## Setup

- 'userCount' must match the intended amount of running users.

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


## Run the project

- Each Application must be simultaneously running on different terminals.
- After starting the clients the available commands can be displayed using 'help'.
- Suggested sequence of commands:
    1. Perform 'step' on a client until there are no more epochs available;
    2. Perform 'submit' of multiple client reports, so that they are stored on the server (on the client interface);
    3. Perform 'obtain' of those reports (on the client interface);
    4. Perform 'obtainLocationReport' of some of those reports on the HA interface;
    5. Perform 'obtainUsersAtLocation' of some known locations on the HA interface;
    6. Perform 'exit' on each client and HA to safely exit the programs.

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

***Note:** Make sure to run 'userCount' users before interacting with the clients*

<br/>


To run the **HealthAuthorityApplication**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.healthauthority.HealthAuthorityApplication
```

<br/>


## Test

- In order to run the tests a server must also be running.

<br/>


To run the tests:
```bash

```

<br/>
