package com.excudo.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the hierarchical timing structure of slide animations
 */
public class TimingTree {
  private TimingNode rootNode;

  public TimingTree() {
    // Empty tree
  }

  public void setRootNode(TimingNode rootNode) {
    this.rootNode = rootNode;
  }

  public TimingNode getRootNode() { return rootNode; }

  public int getNodeCount() {
    return rootNode != null ? rootNode.getTotalNodeCount() : 0;
  }

  public List<TimingNode> getAllNodes() {
    List<TimingNode> allNodes = new ArrayList<>();
    if (rootNode != null) {
      rootNode.collectAllNodes(allNodes);
    }
    return allNodes;
  }


  @Override
  public String toString() {
    return String.format("TimingTree{%d nodes}", getNodeCount());
  }
}
