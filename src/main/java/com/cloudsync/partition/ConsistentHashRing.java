package com.cloudsync.partition;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Consistent Hashing implementation for data partitioning across multiple storage nodes.
 * Uses virtual nodes to ensure even distribution and minimize reshuffling when nodes change.
 */
@Slf4j
public class ConsistentHashRing {

    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final Map<String, Integer> nodeVirtualNodeCount = new HashMap<>();
    private final int virtualNodes;

    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    public ConsistentHashRing(List<String> nodes, int virtualNodes) {
        this.virtualNodes = virtualNodes;
        nodes.forEach(this::addNode);
    }

    /**
     * Add a physical node to the hash ring with virtual nodes.
     * Virtual nodes ensure even distribution even for nodes with different capacities.
     */
    public void addNode(String nodeName) {
        if (nodeVirtualNodeCount.containsKey(nodeName)) {
            log.warn("Node {} already exists in the ring", nodeName);
            return;
        }

        for (int i = 0; i < virtualNodes; i++) {
            String virtualNodeName = getVirtualNodeName(nodeName, i);
            long hash = hash(virtualNodeName);
            ring.put(hash, virtualNodeName);
        }
        nodeVirtualNodeCount.put(nodeName, virtualNodes);
        log.info("Added node '{}' with {} virtual nodes to the hash ring", nodeName, virtualNodes);
    }

    /**
     * Remove a physical node from the hash ring.
     */
    public void removeNode(String nodeName) {
        Integer count = nodeVirtualNodeCount.remove(nodeName);
        if (count == null) {
            log.warn("Node {} not found in the ring", nodeName);
            return;
        }

        for (int i = 0; i < count; i++) {
            String virtualNodeName = getVirtualNodeName(nodeName, i);
            long hash = hash(virtualNodeName);
            ring.remove(hash);
        }
        log.info("Removed node '{}' from the hash ring", nodeName);
    }

    /**
     * Get the node responsible for the given key.
     * Uses the "clockwise" algorithm - finds the first node on the ring going clockwise.
     */
    public String getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty - no nodes available");
        }

        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            entry = ring.firstEntry();
        }

        return extractPhysicalNode(entry.getValue());
    }

    /**
     * Get the N nodes responsible for the given key (for replication).
     * Returns nodes in order of proximity on the ring.
     */
    public List<String> getNodes(String key, int replicas) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty - no nodes available");
        }

        List<String> result = new ArrayList<>();
        Set<String> addedNodes = new HashSet<>();
        long hash = hash(key);
        SortedMap<Long, String> tailMap = ring.tailMap(hash);

        for (Map.Entry<Long, String> entry : tailMap.entrySet()) {
            String physicalNode = extractPhysicalNode(entry.getValue());
            if (addedNodes.add(physicalNode)) {
                result.add(physicalNode);
                if (result.size() >= replicas) break;
            }
        }

        if (result.size() < replicas) {
            for (Map.Entry<Long, String> entry : ring.entrySet()) {
                String physicalNode = extractPhysicalNode(entry.getValue());
                if (addedNodes.add(physicalNode)) {
                    result.add(physicalNode);
                    if (result.size() >= replicas) break;
                }
            }
        }

        return result;
    }

    /**
     * Get the node for a specific file based on its metadata.
     * This creates a deterministic partition based on org+user+path combination.
     */
    public String getNodeForFile(Long organizationId, Long userId, String filePath) {
        String key = String.format("%d:%d:%s", organizationId, userId, filePath != null ? filePath : "root");
        return getNode(key);
    }

    private String getVirtualNodeName(String nodeName, int virtualIndex) {
        return nodeName + "-VN" + virtualIndex;
    }

    private String extractPhysicalNode(String virtualNodeName) {
        int vnIndex = virtualNodeName.lastIndexOf("-VN");
        if (vnIndex > 0) {
            return virtualNodeName.substring(0, vnIndex);
        }
        return virtualNodeName;
    }

    /**
     * MurmurHash3-inspired hash function for consistent distribution.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    public int getTotalVirtualNodes() {
        return ring.size();
    }

    public Set<String> getPhysicalNodes() {
        return nodeVirtualNodeCount.keySet();
    }

    public int getVirtualNodeCount(String nodeName) {
        return nodeVirtualNodeCount.getOrDefault(nodeName, 0);
    }

    public void printRingDistribution() {
        log.info("=== Consistent Hash Ring Distribution ===");
        log.info("Physical nodes: {}", nodeVirtualNodeCount.keySet());
        log.info("Total virtual nodes: {}", ring.size());
        log.info("Virtual nodes per physical node: {}",
                nodeVirtualNodeCount.isEmpty() ? 0 : nodeVirtualNodeCount.values().iterator().next());
    }
}
