# ZMail
ZIO-NIO based SMTP server using ZIO-gRPC to communicate between server and frontend.

## Purpose
The purpose of this project is to demonstrate what is currently possible when developing micro-services using only ZIO libraries.
As a business case it implements an email server for inboxes that are created on the fly. So when an email comes in for test@domain, a mailbox for 'test' is automatically created and the mail is 'stored' in it.
The contents of the user's mailbox can be inspected by a frontend developed with ZIO-gRPC and Scala.JS.

The project was discussed at functionalscala.com 2020 conference.

## How to get started
To run this project, you need to have two main components up and running:
1. server-side: a ZIO app listening on port 8125 (configurable) for SMTP traffic and on port 9000 for gRPC requests
2. frond-end: a web app communicating with the server-side to lookup the mailbox for a given user

### Starting the server
The server can be started by issuing the following command:
```scala
sbt server/run
``` 
Select zmail.Main. You should see the following message:
```
[info] running (fork) zmail.Main
[info] Mounting SMTP server on port 8125
[info] Zmail has started - both smtp and grpc services are running
```
Make sure to keep this terminal open.

### Starting the frontend
Running the frontend is a bit more complex due to the gRPC protocol. First you need to compile the frontend sources to javascript:
```scala
sbt webapp/fullOptJS::webpack
```
The first time you do this it will take several minutes (10+!) to download all its dependencies.
Once compiled you can run the frontend in two docker containers by issuing:
```bash
docker-compose up
```
It will start two containers:
1. an nginx container to serve the static content
2. an envoy container to route traffic and handle gRPC

Now you can read your mails by going to http://localhost:8080 or go directly to the test account http://localhost:8080/mailbox.html?name=test

### Sending mails to the server
You can use a test program to generate emails:
```scala
sbt server/run
```
Select SendEmail and an email will be sent to test@localhost. 

## Credits
The gRPC part of this project is largely based on the example project https://github.com/thesamet/AnyHike

## Contribute
If you like ZMail, great! You will probably also notice that many improvements are still possible - if you feel like working on ZMail then please let me know!

Thanks, Willem 


