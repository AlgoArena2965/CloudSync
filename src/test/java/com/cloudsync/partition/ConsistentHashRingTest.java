package com.cloudsync.partition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    private ConsistentHashRing hashRing;

    @BeforeEach
    void setUp() {
        hashRing = new ConsistentHashRing(List.of("node1", "node2", "node3"), 100);
    }

    @Test
    @DisplayName("Should return a node for any key")
    void testGetNode() {
        String node = hashRing.getNode("test-file-1");
        assertNotNull(node);
        assertTrue(List.of("node1", "node2", "node3").contains(node));
    }

    @Test
    @DisplayName("Should return same node for same key (deterministic)")
    void testDeterministicDistribution() {
        String node1 = hashRing.getNode("same-key-123");
        String node2 = hashRing.getNode("same-key-123");
        assertEquals(node1, node2);
    }

    @Test
    @DisplayName("Should distribute keys across nodes")
    void testDistribution() {
        int[] nodeCounts = new int[3];
        for (int i = 0; i < 1000; i++) {
            String node = hashRing.getNode("file-" + i);
            if ("node1".equals(node)) nodeCounts[0]++;
            else if ("node2".equals(node)) nodeCounts[1]++;
            else if ("node3".equals(node)) nodeCounts[2]++;
        }

        // Each node should get roughly 1/3 of keys
        for (int count : nodeCounts) {
            assertTrue(count > 200, "Each node should receive at least 200 keys out of 1000");
            assertTrue(count < 500, "No node should receive more than 500 keys out of 1000");
        }
    }

    @Test
    @DisplayName("Should return multiple replica nodes")
    void testGetReplicaNodes() {
        List<String> replicas = hashRing.getNodes("test-key", 2);
        assertEquals(2, replicas.size());
        assertNotEquals(replicas.get(0), replicas.get(1));
    }

    @Test
    @DisplayName("Should add and remove nodes dynamically")
    void testAddRemoveNode() {
        hashRing.addNode("node4");
        assertEquals(4, hashRing.getPhysicalNodes().size());

        hashRing.removeNode("node1");
        assertEquals(3, hashRing.getPhysicalNodes().size());
        assertFalse(hashRing.getPhysicalNodes().contains("node1"));
    }

    @Test
    @DisplayName("Should handle empty ring gracefully")
    void testEmptyRing() {
        ConsistentHashRing emptyRing = new ConsistentHashRing(50);
        assertThrows(IllegalStateException.class, () -> emptyRing.getNode("any-key"));
    }

    @Test
    @DisplayName("Should partition by file metadata")
    void testGetNodeForFile() {
        String node = hashRing.getNodeForFile(1L, 100L, "path/to/file.txt");
        assertNotNull(node);
        assertTrue(List.of("node1", "node2", "node3").contains(node));

        // Same inputs should always produce same node
        String node2 = hashRing.getNodeForFile(1L, 100L, "path/to/file.txt");
        assertEquals(node, node2);
    }

    @Test
    @DisplayName("Should report correct virtual node count")
    void testVirtualNodeCount() {
        assertEquals(300, hashRing.getTotalVirtualNodes()); // 3 nodes * 100 virtual nodes
        assertEquals(100, hashRing.getVirtualNodeCount("node1"));
    }
}
