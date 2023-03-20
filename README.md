# Serverless Demo - API and Ingester with Quarkus
A sample project with Quarkus functions to deploy in `Red Hat OpenShift Serverless` environment, with both API and eventing functionality, solving
some common issues.

## API
Requirements:
* Define complex service paths lile `/v1/demo/data`
* Include path parameters like `/v1/demo/data/{index}`
* Include query parameters like `/v1/demo/data/{index}?uppercase=yes`
* Include header parameters like `my-header: ABC`

### Proposed solution
Use the standard `javax-rs` package to define the API and adopt `Quarkus function` only for the event ingestion ([guide](https://quarkus.io/guides/rest-json)).

Example provided in [DemoAPI](./src/main/java/com/redhat/fsi/appeng/serverless/api/DemoAPI.java) and
[DemoFunctions](./src/main/java/com/redhat/fsi/appeng/serverless/api/DemoFunctions.java) classes.

> For the sake of clarity and simplicity, we developed two separate classes, one for functions and one for services, but 
> they could also be defined in the same class.

Main difference between the two approaches:
* Functions cannot be mapped to complex paths, only the given function name can be used as the full path (e.g. `/functionName`)
* Functions allow to model only query parameters (in a `Java Map`, as described [here](https://quarkus.io/guides/funqy-http#get-query-parameter-mapping))
* Functions can be triggered by `CloudEvents`
* Functions can be managed by the  `kn` CLI tool
* Functions can be invoked in different ways (see [here](https://docs.openshift.com/container-platform/4.8/serverless/functions/serverless-developing-quarkus-functions.html#serverless-invoking-quarkus-functions_serverless-developing-quarkus-functions)), while each REST service defines the specific HTTP method to invoke it

### Running the example
Build with (**note**: pre-built images is publicly available at `quay.io/dmartino/serverless-api-ingester-demo:0.1`):
```bash
mvn package
# Replace USERNAME with your quay.io user
docker build -f src/main/docker/Dockerfile.jvm -t quay.io/USERNAME/serverless-api-ingester-demo:0.1 .
docker push quay.io/USERNAME/serverless-api-ingester-demo:0.1
```

Deploy to your current namespace with (**note**: replace the image reference if you built the image):
```bash
# Update image to actual registry if needed
oc apply -f deploy/demo.yaml
```

Monitor the application logs with:
```bash
oc logs -f -l app=serverless-api-ingester-demo -c user-container
```

Run some validation with:
```bash
URL=$(oc get ksvc serverless-api-ingester-demo -ojsonpath='{.status.url}')
# Get all data with GET HTTP
curl -k  -H 'Content-Type: application/json' "$URL/v1/demo/data/"
# Get all data with Function
curl -k  -H 'Content-Type: application/json' "$URL/getData"
# Add value with Function
curl -k  -H 'Content-Type: application/json' "$URL/addValue" -d '"aaa"'
# Get 0-th element with GET HTTP
curl -k  -H 'Content-Type: application/json' "$URL/v1/demo/data/0"
# Get 0-th element with GET HTTP and convert to uppercase
curl -k  -H 'Content-Type: application/json' "$URL/v1/demo/data/0?upper=true"
# Get 0-th element with GET HTTP and append value from header and convert to uppercase 
curl -k  -H 'Content-Type: application/json' "$URL/v1/demo/data/0?upper=true" -H "my-header: bbb"
```

## Ingesting multiple topics
Problem statement:
* Events are ingested into the same application using `KafkaSource`
* The application can receive `CloudEvents` from multiple source topics
* Messages must be processed according to the source topic

Framework constraints:
* Quarkus functions can be triggered only based on the `CE-Type` header of the event
* `KafkaSource` always dispatch events with the same type, `dev.knative.kafka.event`, whatever the source topic

Because of the above limitations, we cannot route the ingesting functions based on the event type, which is always the same.

The following solutions are based on this prerequisite:
* `KnativeKafka` instance is created in `knative-eventing` namespace with:
```yaml
    ...
    source:
      enabled: true
    ...
```

**Preliminary step**: create the Kafka instance `the-cluster` and two sample topics `inbox` and `avro`:
```bash
oc apply -f deploy/kafka.yaml
oc apply -f deploy/kafka-topic.yaml
```

The sample code defines two functions associated to the topics:
1. `eventA` is the function mapped to the `inbox` topic
2. `eventB` is the function mapped to the `avro` topic

The next two solutions show how we can implement the given requirements for these two sample functions.

### Source routing
With this solution, a third function `router` is introduced, and it's mapped to the `dev.knative.kafka.event` event type (see [application.properties](./src/main/resources/application.properties)).

The function code detects the source topic by parsing the `source` field of the event, which comes in the form of:
```yaml
  ...
  source: /apis/v1/namespaces/default/kafkasources/kafka-source#avro
  ...
```
The routing code is defined in the `onRouterEvent` method of [DemoIngester](./src/main/java/com/redhat/fsi/appeng/serverless/ingester/DemoIngester.java).

Deploy the `KafkaSource` [all-to-router](./deploy/kafka-source-source-routing.yaml) that listens both the topics and triggers the routing function:
```bash
# Update NAMESPACE to match your current namespace
oc apply -f deploy/kafka-source-source-routing.yaml 
```

Monitor the application logs with:
```bash
oc logs -f -l app=serverless-api-ingester-demo -c user-container
```

Inject events to `inbox` topic and verify that the router forwards the same to the associated function (**note**: use "" to define the event data because we're
assuming it is a JSON string):
```bash
oc exec -it the-cluster-kafka-0 -- bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic inbox
"an event"
"another one"
```

Sample log from the application:
```bash
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) router invoked from /apis/v1/namespaces/default/kafkasources/all-to-router#inbox with "an event"
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) Routing to topic inbox
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) eventA invoked with "an event"
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) router invoked from /apis/v1/namespaces/default/kafkasources/all-to-router#inbox with "another one"
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) Routing to topic inbox
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) eventA invoked with "another one"
```

If you send the events to the `avro` topic, these will be routed to the `eventB` function.

Key points for this solution:
1. Introduce a routing function based on the `source` field
   *  Because of the internal routing, the actual processing methods (`onEventA` and `onEventB`) do not need the `Funqy` annotations
2. Map the routing function trigger to event type `dev.knative.kafka.event`

### Path based routing
With this solution, the mapping is moved to the definition of the `KafkaSource` instances. Two instances are needed, one for each topic, and an additional
element defines the associated function using an explicit conytext path, like:
```yaml
  ...
  sink:
    uri: /eventB
    ref:
      apiVersion: v1
      kind: Service
      name: serverless-api-ingester-demo
  ...
```

This means that the event is not forwarded to the root URL but to the path defined in the field `sink.uri`.


Deploy the `KafkaSource`s [inbox-to-event-a and avro-to-event-b](./deploy/kafka-source-path-routing.yaml) that listen the topics and invoke the processing function:
```bash
# Remove the other instance as it would try to manage the events twice
oc delete -f deploy/kafka-source-source-routing.yaml
# Update NAMESPACE to match your current namespace
oc apply -f deploy/kafka-source-path-routing.yaml 
```

Monitor the application logs with:
```bash
oc logs -f -l app=serverless-api-ingester-demo -c user-container
```

Inject events to `inbox` topic and verify that the invocation is sent to the associated function without any intermediate routing:
```bash
oc exec -it the-cluster-kafka-0 -- bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic inbox
"an event"
"another one"
```

Sample log from the application:
```bash
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) eventA invoked with "an event"
[com.red.fsi.app.ser.ing.DemoIngester] (executor-thread-0) eventA invoked with "another one"
```

If you send the events to the `avro` topic, these will be routed to the `eventB` function.

Key points for this solution:
1. Define one Quarkus function for each managed topic
   *  The routing function is not relevant for this example and could be removed
2. No need to map the functions to any trigger
3. Define one `KafkaSource` for each topic and include the `sink.uri` field to invoke the specific function


