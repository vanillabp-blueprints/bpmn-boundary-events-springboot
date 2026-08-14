# bpmn-boundary-events

Puts two boundary events on one waiting task: a non-interrupting one that runs beside it
and an interrupting one that ends it. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It carries the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Blueprint-specific names, each occurring in more than one place:

|           Name           |                                                 Where it occurs                                                 |
|--------------------------|-----------------------------------------------------------------------------------------------------------------|
| `requestPartnerApproval` | the `@WorkflowTask` method, the Camunda 7 `camunda:delegateExpression` and the Camunda 8 `zeebe:taskDefinition` |
| `partner-rejected`       | the constant `Workflow.PARTNER_REJECTED` and the `errorCode` of `bpmn:error` in the model                       |
| `informCustomer`         | the `@WorkflowTask` method behind the waiting task and the task definition of that service task                 |
| `noteRejection`          | the `@WorkflowTask` method on the error path and the task definition of that service task                       |

The error code is the contract between code and model: if the two drift apart, the canceled
task is not caught by the boundary event and the workflow ends as an incident.

## Core files

|                                            File                                            |                                                  Why it matters                                                   |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the timer catch event, the waiting task and the INTERRUPTING timer boundary event on it                           |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | the `@WorkflowTask` method of that task: aggregate, `@TaskId`, `@TaskEvent`. Returning does NOT complete the task |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `completeTask`; nothing cancels the task from the application here, the timer does that                           |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | sends the request once, keeps the task id, answers the task when the surrounding system replies                   |
| `loan-approval/src/main/java/.../loanapproval/PartnerApprovalClient.java`                  | the port to the surrounding system, so a test can put a simulator in its place                                    |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the callback the answer arrives at, carrying the task id                                                          |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `partnerApprovalTaskId`, plus the attributes the service tasks behind the waiting task write                      |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per way the task ends: still open, completed, canceled                                                   |

## Boilerplate files

|                                      File                                      |                                             Purpose                                              |
|--------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                     | the BPMS profiles and the VanillaBP BOM import                                                   |
| `loan-approval/pom.xml`                                                        | `vanillabp-spring-boot-support`, never an adapter                                                |
| `application/pom.xml`                                                          | the BPMS adapter, the only place a BPMS is named                                                 |
| `application/src/main/java/.../Application.java`                               | the Spring Boot application, in the parent package of the module                                 |
| `application/src/main/resources/application.yaml`                              | the datasource, and the optional import of the file below                                        |
| `application/src/main/camunda7/resources/camunda7-webapps.yaml`                | the demo user of Camunda's web applications; on the classpath in the Camunda 7 profile only      |
| `loan-approval/src/main/java/.../loanapproval/LocalPartnerApprovalClient.java` | stand-in for the surrounding system so the blueprint runs alone; replace it with the real client |
| `loan-approval/src/test/java/.../TestApplication.java`                         | the minimal application the module's test boots                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                      | base class of the integration test: waits for workflow progress                                  |
| `loan-approval/src/test/java/.../Simulator.java`                               | base class of a stand-in for a surrounding system                                                |
| `application/src/test/java/.../ApplicationSmokeTest.java`                      | boots the application, which validates the BPMN-to-code wiring                                   |
| `docs/loan_approval.png`                                                       | the picture of the process the README shows, rendered from the BPMN model                        |

`TestApplication`, `WorkflowModuleTest`, `Simulator` and `ApplicationSmokeTest` are
identical in every blueprint - copy them unchanged.

## Adding this blueprint to an existing project

1. Decide per event whether it interrupts. Something that happens ALONGSIDE the task is
   non-interrupting (`cancelActivity="false"`) and needs its own branch ending in its own
   end event; something that ENDS the task is interrupting, which is the default. Putting
   the wrong one in is not a compile error, it is a process that either never stops waiting
   or stops waiting too early.
2. Add an attribute for the task id to the workflow aggregate. Without it the task cannot
   be answered later, because its id is the only handle to it.
3. Add an interface for the surrounding system, and the real client as its implementation.
   Do not call the system from `WorkflowTaskHandler`.
4. Add the `@WorkflowTask` method named after the task definition. It takes the aggregate,
   `@TaskId` for the id and `@TaskEvent` to tell delivery from cancellation, and it calls
   `Service` for each of the two. Never complete the task there.
5. Add the business methods to `Service`: one that returns early if the request went out
   already, sends it and stores the id; one that drops the id when the task was canceled;
   and one per answer the API accepts. Annotate the API-facing ones with `@Transactional`,
   never the ones the task handler calls.
6. Have the answering method reject a task id which is not the one stored on the aggregate.
   An answer arrives from outside and outlives the task it belongs to.
7. Add `completeTask` and `cancelTask` calls to `Workflow`, one method per business event,
   and keep the BPMN error code there as a constant.
8. Add the callback endpoint carrying the task id, and log the URLs continuing the process
   when the request goes out.
9. Copy `LoanApprovalIT` and write one test per way the task ends. Do not make a test wait
   for a fixed number of repetitions of a cyclic timer: `R2/PT1S` says what the model asks
   for, not what an engine gets done before the deadline, and that number differs between
   BPMS.

Two branches of one workflow may run at the same time - that is what a non-interrupting
event creates - and both load and save the same workflow aggregate. Keeping what each branch
writes disjoint is necessary and not sufficient: JPA saves the whole row, so the branch that
commits second writes back the values it read at its start and the other branch's work is
gone, silently. **Put `@DynamicUpdate` on the aggregate of a workflow with a non-interrupting
boundary event**, which makes Hibernate write only the columns that changed. Two branches
writing the same attribute needs a `@Version` column instead, or a different model.

Never rely on the cancellation reaching your handler: not every BPMS reports it. Answering
a task that is gone is a no-op, so the correctness of your code must not depend on hearing
about it.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass:

- the surrounding system was asked once, the aggregate carries the task id, and the service
  task behind the waiting one has NOT run, which is what proves the task stays open,
- after `completeTask` that service task has run,
- after `cancelTask` the service task on the error path has run and the stored id is gone.

If the task is completed as soon as the handler returns, the model wires it in a way that
does not allow it to stay open - on Camunda 7 that is `camunda:expression`. If the workflow
ends in an incident instead of taking the error path, the error code in the code and the one
in the model differ.

Do not report success without having run this.
