### How to Use
1. Buld the plugin jar

```
mvn package
```

2. Add the jar to your `sharedLib` directory in solr machine
3. Add entry for `sharedLib` in your `solr.xml`

```
<str name='sharedLib'>/opt/solr/sharedLib</str>
```


### How it works
1. Refer `https://lucene.apache.org/solr/guide/7_4/solrcloud-autoscaling-triggers.html`
2. `AddResource` implements `TriggerAction` which can be configured to run a set of operations based on solr's predefined autoscaling triggers
3. `AddResource` does the following
    - Checks if a pod already exists and doesn't contain a replica of configured collection
    - If above is true adds a replica to it
    - If check fails, adds one solr pod to kube cluster and adds a replica to it

### Assumptions
- Assumes we use only one shard for a collection, can be modified to support multiple shards    