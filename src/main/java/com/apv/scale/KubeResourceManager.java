package com.apv.scale;

import com.apv.scale.exception.ResourceManagerException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashSet;

@Log4j2
public class KubeResourceManager implements ResourceManager {
    //todo read from properties or env
    private static final String KUBE_SOLR_TAG = "solr";
    private static final long POD_WAIT_TIME = 10000L;
    private static final int POD_POLL_TRIES = 100;
    private static final String SOLR_ADDRESS_SUFFIX = ".solr-svc.default.svc.cluster.local:8983_solr";

    @Override
    public String allocateResource() throws ResourceManagerException {
        KubernetesClient kube = new DefaultKubernetesClient();
        StatefulSet solr = kube.apps().statefulSets().withName(KUBE_SOLR_TAG).get();
        Integer replicas = solr.getSpec().getReplicas();
        HashSet<String> prePodNames = getPodNames(kube);
        kube.apps().statefulSets().inNamespace("default").withName(KUBE_SOLR_TAG).scale(replicas + 1);
        waitForIt();
        HashSet<String> postPodNames = getPodNames(kube);
        if (postPodNames.size() == prePodNames.size()) {
            throw new ResourceManagerException("Failed to upscale solr pod in" + POD_WAIT_TIME + " ms");
        }
        //removing all known pods from list
        for (String prePod : prePodNames) {
            postPodNames.remove(prePod);
        }
        String newAddedPod = new ArrayList<String>(postPodNames).get(0);
        log.info("Added pod:" + newAddedPod);
        int i = 0;
        while (i < POD_POLL_TRIES) {
            waitForIt();
            PodList list = kube.pods().withLabel("app", KUBE_SOLR_TAG).list();
            for (Pod pod : list.getItems()) {
                if (pod.getMetadata().getName().equals(newAddedPod)) {
                    String phase = pod.getStatus().getPhase();
                    if ("Running".equals(phase)) {
                        return newAddedPod + SOLR_ADDRESS_SUFFIX;
                    }
                }
            }
            i++;
        }
        throw new ResourceManagerException("Failed to upscale solr pod in" + POD_WAIT_TIME * POD_POLL_TRIES + " ms");
    }

    private HashSet<String> getPodNames(KubernetesClient kube) {
        PodList preList = kube.pods().withLabel("app", KUBE_SOLR_TAG).list();
        String names = "";
        HashSet<String> prePodIds = new HashSet<>();
        for (Pod pod : preList.getItems()) {
            prePodIds.add(pod.getMetadata().getName());
            names = names + "," + pod.getMetadata().getName();
        }
        return prePodIds;
    }

    private void waitForIt() throws ResourceManagerException {
        try {
            Thread.sleep(POD_WAIT_TIME);
        } catch (InterruptedException e) {
            throw new ResourceManagerException(e);
        }
    }
}
