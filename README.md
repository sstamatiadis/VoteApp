# VoteApp

VoteApp is an e-voting platform that allows users to create polls and vote either publicly or privately. The poll questions have the flexibility of being either single or multiple choice. Users can register and participate in public polls, get an invitation for private ones or create their own private polls and invite other voters. This project serves as a simple proof of concept for online voting as a part of my thesis. The aim was to create an android app and simultaneously discover the difficulties and problems of e-voting systems.

## Prerequisites

- Java version >= **1.8**
- scala-sbt >= **0.13.13**
- Play Framework version is **2.5.4** (only [download](https://www.playframework.com/download#alternatives) if the project is not working)


## Installation

 1. Download, install PostgreSQL from the official website and create a blank database for the server. 
 2. Download and install Java and sbt. Make sure the versions match the prerequisites. Set the Java path if needed (Windows).
 3. Clone this repository in a folder.
 4. Edit */conf/application.conf*, locate the db object and change the database credentials to the ones from step 1.
 5. Run `sbt` (*it's an sbt shell now*) in the top level of the project and after it has downloaded the dependencies, execute `run` to bring the server online. The server should be listening at **localhost:9000**.
 6. (Optional) To activate HTTPS you need to add `-Dhttps=enabled` as a parameter to the `run`. Check Play's [website](https://www.playframework.com/documentation/2.5.x/ConfiguringHttps#Providing-configuration) on how to configure it.

## Developers

This is a Play Framework project written in Java. It tries to follow the **RESTful architecture** design. The built-in **sbt-play-ebean** plugin is taken advantage of in order to create an ORM layer between the models and the relational database using **JPA mapping annotations**. The project was developed to work with **PostgreSQL** but it shouldn't be trivial to swap with other RDBMS technologies.

Currently there is no friend-list system handled by the platform. This is handled separately from the client program e.g [VoteApp_Android](https://github.com/sstamatiadis/VoteApp_Android).

### Resources

- Users
- Polls
- Participations
- Votes

The above resources are represented in **JSON** and follow the [JSON-API specification](http://jsonapi.org/). Check the documentation page `./public/html/documentation.html` to explore all available endpoints and their requirements by opening it with a browser.

### Database schema

<p align="center">
  <img src="../assets/img/schema.png?raw=true">
</p>

## Disclaimer

Although basic security practices are followed (input validation, bcrypt password encryption, JWT tokens), this platform is not to be used by any means for critical elections without further investigation and development.

## License

This project is licensed under the MIT License - see the [LICENSE.md](./LICENSE.md) file for details.
