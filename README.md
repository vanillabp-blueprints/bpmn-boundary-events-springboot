![Header](./readme/vanillabp-headline.png)

# Boundary events

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

Something happens while a task is open: a deadline passes, a reminder is due, a customer
withdraws. A boundary event is how the process reacts, and it comes in two kinds whose
difference decides what your code has to handle. This blueprint puts both on the same task.

## What this blueprint shows

![The loan approval process, with a non-interrupting reminder and an interrupting deadline on one task](docs/loan_approval.png)

The loan approval of the base blueprint. A partner has to approve the loan, and the task
asking them carries two boundary events:

- **Non-interrupting** (`cancelActivity="false"`), the reminder. It fires twice while the
  task is open, sends a reminder each time and the task **stays open**. The token simply
  splits: the reminder branch runs to its own end event while the main branch keeps
  waiting.
- **Interrupting** (the default), the deadline. When it fires, the task is **canceled** and
  the workflow leaves it for good, taking the path behind the event.

For the application the difference is what it must handle:

- The non-interrupting one is an ordinary task on a side branch. Nothing is canceled, so
  nothing is reported as canceled, and the task id the application kept still works
  afterwards - the test proves exactly that by answering the task after a reminder went out.
- The interrupting one ends the wait. The open task is gone, and the handler is called once
  more with `@TaskEvent CANCELED` so it can drop what it kept. **Not every BPMS reports
  that**, so no code may depend on it: answering a task that is gone is a no-op either way.
  Which engines deliver cancellations is on
  [the adapter's wiki page](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters),
  and this blueprint asserts the cleared id only where it is delivered.

The two events also compete, which is worth seeing: the reminder cycle is `R2/PT1S` and the
deadline `PT10S`, far enough apart that the order is not a matter of luck. Timers fire when
the BPMS gets to them, and an engine which polls for due jobs may execute two of them in one
sweep - a process whose correctness depends on which of two events comes first is a process
waiting to surprise somebody.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-springboot):

|            File            |                                      What is different                                       |
|----------------------------|----------------------------------------------------------------------------------------------|
| `loan_approval.bpmn`       | a task waiting for a partner with TWO boundary events on it, one interrupting and one not    |
| `WorkflowTaskHandler.java` | the method of the waiting task plus one per branch behind an event                           |
| `Workflow.java`            | `completeTask` in addition to `startWorkflow`                                                |
| `Service.java`             | sends the request, sends reminders while it waits, answers the task, notes a missed deadline |
| `Aggregate.java`           | `partnerApprovalTaskId`, `remindersSent` and `timedOut`                                      |
| `LoanApprovalIT.java`      | a reminder leaving the task open, an answer in time, and the deadline ending the wait        |
| `pom.xml`                  | hands the BPMS of the build to the tests, for the assertion about cancellations              |

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

Running it on another BPMS is a Maven profile, not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run and be pointed at. Start one, then
add its address to `application/src/main/resources/application.yaml` and to
`loan-approval/src/test/resources/application.yaml`:

```yaml
vanillabp:
  adapters:
    camunda8:
      rest-address: http://localhost:8080
      # Nothing else is needed: this adapter keeps workflow modules apart by nothing at all
      # ('name-clash-avoidance: none') unless told otherwise, because a cluster started from
      # the stock image has multi-tenancy switched off and rejects a tenant per module. The
      # adapter warns about it while booting - with one workflow module the identifiers are
      # unique anyway. Set 'name-clash-avoidance: use-prefix' to have VanillaBP prefix them.
```

Start the application:

```bash
mvn -pl application spring-boot:run
```

Booting logs a warning per workflow module: both Camunda adapters start out with
`name-clash-avoidance: none`, so nothing keeps the identifiers of one workflow module apart
from those of another, and the adapter asks for a decision instead of picking one. One module
cannot collide with itself, so this blueprint leaves it at that. Answering the question is one
property, `vanillabp.adapters.<id>.accept-unscoped-identifiers: true`, and the modes a BPMS
offers are in
[the wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided).

Start a loan approval. This is the only URL you need:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

The partner is asked, and from then on both clocks run:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50
Partner was asked to approve loan approval '0f7c…' (5000 at a rating of 50)
Loan approval '0f7c…' waits for the partner, but not forever - the task carries a deadline of ten seconds, and reminders go out while it runs:
  Approved -> http://localhost:8080/api/loan-approval/0f7c…/partner-approved/1a2b…
```

Reminders go out while nothing happens, and the task stays open:

```
Reminder 1 for loan approval '0f7c…' - the partner is still expected to answer
Reminder 2 for loan approval '0f7c…' - the partner is still expected to answer
```

Open the URL any time before the deadline and the answer is still accepted:

```
The partner approved loan approval '0f7c…'
The customer of loan approval '0f7c…' was informed
```

Wait longer, and the interrupting event ends the wait:

```
The partner request of loan approval '4b21…' was canceled
Nobody answered for loan approval '4b21…' in time
```

The first of those two lines is the cancellation reaching the same handler that sent the
request - on a BPMS that reports cancellations. Opening the URL afterwards answers that this
request is not open any more, which the application decides on its own.

While the application runs on Camunda 7, Camunda's own web applications are served at

```
http://localhost:8080/camunda
```

Log in with `demo` / `demo`. Cockpit shows both tokens while the reminder branch runs, which
is the clearest picture of what non-interrupting means. The user comes from
`application/src/main/camunda7/resources/camunda7-webapps.yaml` and exists so that the
blueprint can be operated without setting one up; an application with an identity provider
of its own leaves that section out.

The Camunda 8 profile ships neither the dependency nor that file. Its tooling is part of
the cluster, and the file names a Camunda 7 adapter id, which VanillaBP would rightly
refuse to start with.

## How it works

|                                          File                                          |                                              Role                                              |
|----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process: one waiting task carrying a non-interrupting and an interrupting boundary event   |
| `.../loanapproval/WorkflowTaskHandler.java`                                            | the method of the waiting task: `@TaskId` to keep, `@TaskEvent` to hear about the cancellation |
| `.../loanapproval/Service.java`                                                        | asks the partner once, answers the task, and notes a missed deadline                           |
| `.../loanapproval/Workflow.java`                                                       | `completeTask`, the only place `ProcessService` is used                                        |
| `.../loanapproval/model/Aggregate.java`                                                | the task id, the answer and whether the deadline ran out                                       |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | answered in time, and not answered at all                                                      |

What the two events have in common: the BPMS decides when they fire and the application only
receives the work. What separates them is the token. A non-interrupting event adds one, so
the reminder runs beside a task that is still open; an interrupting event takes the token
away from the task, which is what makes the task disappear.

That is also why a reminder must not touch what the open task is about to write: two
branches of the same workflow may run at the same time, and both load and save the same
aggregate. Here the reminder only counts, and the answer only writes its own attributes.

The tests wait for the aggregate rather than for a clock. A timer fires when the BPMS gets
to it, so a test that sleeps for exactly three seconds is a test that fails on a slow
machine.

## Documentation

- [Workflow tasks](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-tasks#parameters): `@TaskId` and `@TaskEvent`, and what a lifecycle event delivers
- [Completing and canceling asynchronous tasks](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-tasks#completing-and-canceling-asynchronous-tasks): the rules the answer follows, and what happens to a task that is gone
- [BPMS adapters](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters): which engine reports a cancellation to the application, and which does not
- the wiki of the BPMS adapter you use: how it executes timers, and whether it reports a canceled task to the application

This blueprint is developed in the monorepo
[`blueprints`](https://github.com/vanillabp-blueprints/blueprints). This repository is a
read-only mirror, **issues and pull requests belong there.**

## Noteworthy & Contributors

[VanillaBP](https://www.github.com/vanillabp/spi-for-java) was developed by [Phactum](https://www.phactum.at) with the
intention of giving back to the community as it has benefited the community in the past.

![Phactum](./readme/phactum.png)

## License

Copyright 2026 Phactum Softwareentwicklung GmbH

Licensed under the Apache License, Version 2.0
