package com.cloudsync.partition;

import com.cloudsync.config.CloudSyncProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NodePartitionService {

    private final CloudSyncProperties properties;
    private ConsistentHashRing hashRing;

    @PostConstruct
    public void init() {
        List<String> nodes = Arrays.asList(
                properties.getConsistentHash().getNodes().split(","));
        int virtualNodes = properties.getConsistentHash().getVirtualNodes();

        hashRing = new ConsistentHashRing(nodes, virtualNodes);
        hashRing.printRingDistribution();

        log.info("NodePartitionService initialized with {} physical nodes and {} virtual nodes per node",
                nodes.size(), virtualNodes);
    }

    /**
     * Get the storage node for a file based on its metadata.
     */
    public String getNodeForFile(Long organizationId, Long userId, String filePath) {
        String node = hashRing.getNodeForFile(organizationId, userId, filePath);
        log.debug("File (org={}, user={}, path={}) partitioned to node: {}",
                organizationId, userId, filePath, node);
        return node;
    }

    /**
     * Get all replica nodes for a file (for replication strategy).
     */
    public List<String> getReplicaNodes(Long organizationId, Long userId, String filePath, int replicas) {
        String key = String.format("%d:%d:%s", organizationId, userId, filePath != null ? filePath : "root");
        return hashRing.getNodes(key, replicas);
    }

    /**
     * Add a new node to the cluster (dynamic scaling).
     */
    public void addNode(String nodeName) {
        hashRing.addNode(nodeName);
        log.info("Node {} added to the partition ring", nodeName);
    }

    /**
     * Remove a node from the cluster (for graceful shutdown or scaling down).
     */
    public void removeNode(String nodeName) {
        hashRing.removeNode(nodeName);
        log.info("Node {} removed from the partition ring", nodeName);
    }

    /**
     * Get all available nodes in the cluster.
     */
    public List<String> getAllNodes() {
        return List.copyOf(hashRing.getPhysicalNodes());
    }

    /**
     * Get the ring statistics for monitoring.
     */
    public NodeRingStats getRingStats() {
        return new NodeRingStats(
                hashRing.getPhysicalNodes().size(),
                hashRing.getTotalVirtualNodes(),
                hashRing.getVirtualNodeCount(
                        hashRing.getPhysicalNodes().stream().findFirst().orElse(""))
        );
    }

    public record NodeRingStats(int physicalNodes, int totalVirtualNodes, int virtualNodesPerPhysical) {}
}
