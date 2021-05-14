# S.E.C.
*Sistemas de Elevada Confiabilidade - IST - 2020/2021*

---
## Setup

First, there must be a local *mysql* database named 'sec' which contains a user named 'user' and with 'pass' as its password.

To do so, please refer to these commands:

```mysql
CREATE USER 'user'@'localhost' IDENTIFIED BY 'pass';
GRANT ALL PRIVILEGES ON * . * TO 'user'@'localhost';
```
```mysql
CREATE DATABASE sec0;
```

<br/>


Before running the clients and the server, the environment (a set of grids per epoch), and the key-pairs of each entity must be generated.

The bellow commands must be run in a terminal inside the root directory of the project (```/sec```).

- 'userCount' must match the intended amount of running users.


To run the **Setup**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.Setup -Dspring-boot.run.arguments="[nX] [nY] [epochCount] [userCount] [serverCount]"
```
***Suggestion:** 3x3 grid with 3 users (the amount of epochs is not that relevant)*

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
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.server.ServerApplication -Dspring-boot.run.arguments="[serverId] [serverCount] [userCount]"
```

<br/>


To run the **ClientApplication**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.client.ClientApplication -Dspring-boot.run.arguments="[userId] [serverCount]"
```
***Suggestion:** 3 users using userIds 0, 1 and 2*

***Note:** Make sure to run 'userCount' users before interacting with the clients*

<br/>


To run the **HealthAuthorityApplication**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.healthauthority.HealthAuthorityApplication -Dspring-boot.run.arguments="[serverCount]"
```

<br/>


## Test


Before running the tests for the first time, the database should be reset:
```mysql
DROP DATABASE sec;
CREATE DATABASE sec;
```

<br/>

In order to run the tests a server must also be running:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.server.ServerApplication
```


Then, to run the tests:
```bash
./mvnw -Dtest=pt.tecnico.sec.server.ServerApplicationTests test
```

<br/>

***Note:** Make sure to rerun the server before retesting the project*

<br/>
