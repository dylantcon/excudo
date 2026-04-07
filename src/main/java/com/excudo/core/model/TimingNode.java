package com.excudo.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single node in the timing hierarchy
 */
public class TimingNode {
  private final String nodeId;
  private final String nodeType;
  private final String duration;
  private String delay;  // Added delay support
  private final List<TimingNode> children = new ArrayList<>();

  public TimingNode(String nodeId, String nodeType, String duration) {
    this.nodeId = nodeId;
    this.nodeType = nodeType;
    this.duration = duration;
    this.delay = "0";  // Default delay
  }

  public void addChild(TimingNode child) {
    children.add(child);
  }

  public void setDelay(String delay) {
    this.delay = delay;
  }

  public String getNodeId() { return nodeId; }
  public String getId() { return nodeId; }  // Alias for Commands
  public String getNodeType() { return nodeType; }
  public String getDuration() { return duration; }
  public String getDelay() { return delay; }
  public List<TimingNode> getChildren() { return new ArrayList<>(children); }
  

  public boolean isClickTrigger() {
    return "indefinite".equals(delay);
  }

  public int getTotalNodeCount() {
    int count = 1; // This node
    for (TimingNode child : children) {
      count += child.getTotalNodeCount();
    }
    return count;
  }

  public void collectAllNodes(List<TimingNode> collector) {
    collector.add(this);
    for (TimingNode child : children) {
      child.collectAllNodes(collector);
    }
  }

  @Override
  public String toString() {
    return String.format("TimingNode{id='%s', type='%s', delay='%s', children=%d}",
        nodeId, nodeType, delay, children.size());
  }
}

