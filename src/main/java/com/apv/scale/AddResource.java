package com.apv.scale;

import com.apv.scale.exception.ResourceManagerException;
import org.apache.solr.client.solrj.cloud.autoscaling.ReplicaInfo;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.cloud.autoscaling.ActionContext;
import org.apache.solr.cloud.autoscaling.TriggerActionBase;
import org.apache.solr.cloud.autoscaling.TriggerEvent;
import org.apache.solr.common.cloud.Replica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AddResource extends TriggerActionBase {
    private static final String ID = "addResource";
    private static final long WAIT_TIME = 10000L;
    private static final int POLL_TRIES = 100;
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ResourceManager resourceManager;

    @Override
    public void init() {
        resourceManager = new KubeResourceManager();
    }

    @Override
    public String getName() {
        return ID;
    }

    @Override
    public void process(TriggerEvent triggerEvent, ActionContext actionContext) throws Exception {
        List<ReplicaInfo> hotReplicas = (List<ReplicaInfo>) triggerEvent.getProperties().get("hotReplicas");
        if (hotReplicas.get(0) instanceof ReplicaInfo) {
            //depending on version, solr can return a ReplicaInfo or just a map
            for (ReplicaInfo hotReplica : hotReplicas) {
                String collection = hotReplica.getCollection();
                invoke(collection);
            }
        } else {
            List<Map<String, Map<String, Object>>> info = (List<Map<String, Map<String, Object>>>)
                    triggerEvent.getProperties().get("hotReplicas");
            for (Map<String, Map<String, Object>> infoOne : info) {
                Iterator<String> iterator = infoOne.keySet().iterator();
                while (iterator.hasNext()) {
                    String it = iterator.next();
                    String collection = infoOne.get(it).get("collection").toString();
                    invoke(collection);
                }
            }
        }
    }

    private void invoke(String collection) throws IOException, InterruptedException, ResourceManagerException {
        List<Replica> replicas = cloudManager.getClusterStateProvider().getCollection(collection).getReplicas();
        if (cloudManager.getClusterStateProvider().getLiveNodes().size() <= replicas.size()) {
            //has no free nodes
            String node = resourceManager.allocateResource();
            int i = 0;
            while (i < POLL_TRIES) {
                Thread.sleep(WAIT_TIME);
                ArrayList<String> liveNodes = new ArrayList<>(cloudManager.getClusterStateProvider().getLiveNodes());
                i++;
                if (liveNodes.contains(node)) {
                    addReplica(collection, node);
                    return;
                }
            }
        } else {
            //has free nodes
            ArrayList<String> liveNodes = new ArrayList<>(cloudManager.getClusterStateProvider().getLiveNodes());
            for (Replica replica : replicas) {
                liveNodes.remove(replica.getNodeName());
            }
            addReplica(collection, liveNodes.get(0));
        }
    }

    private void addReplica(String collection, String node) throws IOException {
        //todo assumes it is single sharded
        CollectionAdminRequest.AddReplica req = CollectionAdminRequest.addReplicaToShard(collection, "shard1");
        req.setNode(node);
        cloudManager.request(req);
    }
}
