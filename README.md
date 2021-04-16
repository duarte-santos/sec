# SEC
*Sistemas de Elevada Confiabilidade - IST - 2020/2021*

---

First, there must be a local *mysql* database named 'sec' which contains a user named 'user' and with 'password' as its password.
To do so, please refer to this commands:
```mysql
CREATE USER 'user'@'localhost' IDENTIFIED BY 'pass';
GRANT ALL PRIVILEGES ON * . * TO 'user'@'localhost';
```
```mysql
CREATE DATABASE sec;
```

Before running the clients and the server, the environment (a set of grids per epoch), and the key-pairs of each entity must be generated.

The bellow commands must be run in a terminal inside the root directory of the project (```/sec```).

---

To run the **EnvironmentGenerator**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="[nX] [nY] [epochCount] [userCount]" -Dstart-class=pt.tecnico.sec.EnvironmentGenerator
```
***Note:** We suggest using a 3x3 grid with 3 users*

To run the **RSAKeyGenerator**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="[userCount]" -Dstart-class=pt.tecnico.sec.RSAKeyGenerator
```
***Note:** We suggest using 3 users*

To run the **ServerApplication**:
```bash
./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.server.ServerApplication
```

To run the **ClientApplication**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="[userId]" -Dstart-class=pt.tecnico.sec.client.ClientApplication
```
***Note:** We suggest using userIds 1-3*