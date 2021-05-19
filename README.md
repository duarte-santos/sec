# S.E.C.
*Sistemas de Elevada Confiabilidade - IST - 2020/2021*

---
## Setup

First, there must be a local *mysql* user named 'user' and with 'pass' as its password.

To do so, please refer to these commands:

```mysql
CREATE USER 'user'@'localhost' IDENTIFIED BY 'pass';
GRANT ALL PRIVILEGES ON * . * TO 'user'@'localhost';
```

<br/>


Before running the clients and the server, the environment (a set of grids per epoch), the key-pairs of each entity and the servers' databases must be generated.

The bellow commands must be run in a terminal inside the root directory of the project (```/sec```).

- 'userCount' must match the intended amount of running users.
- 'serverCount' must match the intended amount of running users.

To run the **Setup**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.Setup -Dspring-boot.run.arguments="[nX] [nY] [epochCount] [userCount] [serverCount]"
```
***Suggestion:** 3x3 grid with 3 users and 3 servers (the amount of epochs is not that relevant)*

<br/>

## Run the project

- Each Application must be simultaneously running on different terminals.
- After starting the clients, the available commands can be displayed using 'help'.
- Suggested sequence of commands:
    1. Run **userCount** Clients, **serverCount** Servers and one Health Authority.
    2. Perform 'step' on a client until there are no more epochs available;
    3. Perform 'submit' of multiple client reports (on the client interface), so that they are stored on the server;
    4. Perform 'obtain' of those reports (on the client interface);
    5. Perform 'proofs' regarding the reported epochs (on the client interface);
    6. Perform 'obtainLocationReport' of some of those reports on the HA interface;
    7. Perform 'obtainUsersAtLocation' of some known locations on the HA interface;
    8. Perform 'exit' on each client and HA to safely exit the programs.

<br/>


To run the **ServerApplication**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.server.ServerApplication -Dspring-boot.run.arguments="[serverId] [serverCount] [userCount]"
```
***Suggestion:** 3 servers using serverIds 0, 1 and 2*

***Note:** Make sure to run 'serverCount' servers before interacting with the clients*


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

**Please note that, after exiting the applications and before re-running the setup, the created databases should be manually deleted. For example, if 2 servers were used, the commands 'drop database sec0;' and 'drop database sec1;' should be run.**

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
