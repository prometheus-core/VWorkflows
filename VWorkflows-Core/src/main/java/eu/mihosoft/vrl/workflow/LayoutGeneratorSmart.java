/*
 * Copyright 2012-2016 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * Please cite the following publication(s):
 *
 * M. Hoffer, C.Poliwoda, G.Wittum. Visual Reflection Library -
 * A Framework for Declarative GUI Programming on the Java Platform.
 * Computing and Visualization in Science, 2011, in press.
 *
 * THIS SOFTWARE IS PROVIDED BY Michael Hoffer <info@michaelhoffer.de> "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Michael Hoffer <info@michaelhoffer.de> OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of Michael Hoffer <info@michaelhoffer.de>.
 */
package eu.mihosoft.vrl.workflow;

import cern.colt.Arrays;
import edu.uci.ics.jung.algorithms.layout.ISOMLayout;
import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.KKLayout;
import edu.uci.ics.jung.algorithms.layout.DAGLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

/** 
 * This class provides a sophisticated implementation for the LayoutGenerator 
 * interface based on layout algorithms implemented in
 * the Java Universal Network/Graph Drawing Framework.
 * 
 * @author Tobias Mertz
 */
public class LayoutGeneratorSmart implements LayoutGenerator {
    
    // parameters:
    private VFlow workflow;
    private DirectedGraph<VNode, Connection> jgraph;
    private boolean recursive;
    private boolean autoscaleNodes;
    private int layoutSelector;
    private double aspectratio;
    private boolean justgraph;
    private boolean launchRemoveCycles;
    private boolean launchSeparateDisjunctGraphs;
    private boolean launchRotate;
    private boolean launchOrigin;
    private boolean launchPushBack;
    private boolean launchDisplaceIdents;
    private boolean launchForcePush;
    private boolean launchAlignNodes;
    private int maxiterations;
    private double scaling;
    private double subflowscale;
    private boolean debug;
    
    // internal fields:
    private VNode[] nodes;
    private Layout<VNode, Connection> layout;
    private int nodecount;
    private int conncount;
    private Point2D graphcenter;
    private Pair<Integer>[] origin;
    private boolean cycle;
    
    /**
     * Default constructor.
     * All parameters are initialized with default values.
     */
    public LayoutGeneratorSmart() {
        this.debug = false;
        initialization();
    }
    
    /**
     * Constructor with debug-functionality.
     * Debug parameter can be set, all other parameters are initialized with 
     * default values.
     * @param pdebug boolean
     */
    public LayoutGeneratorSmart(boolean pdebug) {
        this.debug = pdebug;
        initialization();
        if(this.debug) System.out.println("Creating Layout Generator.");
    }
    
    /**
     * Initializes the fields of the class needed in future methods.
     */
    private void initialization() {
        this.jgraph = new DirectedSparseGraph<>();
        
        // default parameters:
        this.recursive = true;
        this.autoscaleNodes = true;
        this.layoutSelector = 0;
        this.aspectratio = 16. / 9.;
        this.justgraph = false;
        this.launchRemoveCycles = true;
        this.launchSeparateDisjunctGraphs = true;
        this.launchRotate = true;
        this.launchOrigin = false;
        this.launchPushBack = true;
        this.launchDisplaceIdents = true;
        this.launchForcePush = true;
        this.launchAlignNodes = true;
        this.maxiterations = 500;
        this.scaling = 1.2;
        this.subflowscale = 2.;
    }
    
    // <editor-fold desc="getter" defaultstate="collapsed">
    /**
     * Returns the workflow to be laid out.
     * @return VFlow
     */
    @Override
    public VFlow getWorkflow() {
        return this.workflow;
    }
    
    /**
     * If set to true, the layout is applied to all subflows of the given 
     * workflow recursively.
     * default: true
     * @return boolean
     */
    @Override
    public boolean getRecursive() {
        return this.recursive;
    }
    
    /**
     * If set to true, subflow nodes in the given workflow are automatically 
     * scaled to fit their contents.
     * default: true
     * @return boolean
     */
    @Override
    public boolean getAutoscaleNodes() {
        return this.autoscaleNodes;
    }
    
    /**
     * Get the Jung layout that is used in the first step of the algorithm.
     * 0 - ISOM Layout
     * 1 - FR Layout
     * 2 - KK Layout
     * 3 - DAG Layout (Does not terminate if the graph contains cycles)
     * default: 0
     * @return int
     */
    public int getLayoutSelector() {
        return this.layoutSelector;
    }
    
    /**
     * Get the aspect ratio of the initial drawing space of the graph.
     * Width is determined via the longest path in the graph.
     * Height is determined by dividing the width by the aspect ratio.
     * default: 16:9
     * @return double
     */
    public double getAspectratio() {
        return this.aspectratio;
    }
    
    /**
     * If set to true, the layout is applied to a given Jung-Graph of types 
     * <VNode, Connection> instead of a VFlow.
     * Is used for sublayouts by separateDisjunctGraphs().
     * default: false
     * @return boolean
     */
    public boolean getJustgraph() {
        return this.justgraph;
    }
    
    /**
     * If set to true a depth-first-search is performed and all back edges are 
     * removed from the model graph. The layout is then applied without these 
     * edges.
     * Is only run if the graph contains cycles.
     * default: true
     * @return boolean
     */
    public boolean getLaunchRemoveCycles() {
        return this.launchRemoveCycles;
    }
    
    /**
     * If set to true disjunct parts of the model graph will be laid out 
     * separately and then arranged over each other to create the cumulative 
     * layout.
     * Is never run if launchRemoveCycles is false and the graph contains 
     * cycles, because it does not terminate in this case.
     * default: true
     * @return boolean
     */
    public boolean getLaunchSeparateDisjunctGraphs() {
        return this.launchSeparateDisjunctGraphs;
    }
    
    /**
     * If set to true, the graph is rotated to reach an average flow direction 
     * parallel to the horizontal axis.
     * default: true
     * @return boolean
     */
    public boolean getLaunchRotate() {
        return this.launchRotate;
    }
    
    /**
     * If set to true, nodes with an in-degree of 0 will be placed at the 
     * leftmost edge of the drawing space.
     * Is always run if launchSeparateDisjunctGraphs is true, launchRemoveCycles 
     * is false and the graph contains cycles, because separateDisjunctGraphs 
     * does not terminate if the graph contains cycles.
     * default: false
     * @return boolean
     */
    public boolean getLaunchOrigin() {
        return this.launchOrigin;
    }
    
    /**
     * If set to true, all nodes will be pushed to the right of their 
     * predecessors so no edge has a direction with a negative x-component.
     * Is never run if launchRemoveCycles is false and the graph contains 
     * cycles, because it does not terminate in this case.
     * default: true
     * @return boolean
     */
    public boolean getLaunchPushBack() {
        return this.launchPushBack;
    }
    
    /**
     * If set to true, nodes that are placed on the same coordinates will be 
     * displaced slightly, so the forcePush step can move them accordingly.
     * default: true
     * @return boolean
     */
    public boolean getLaunchDisplaceIdents() {
        return this.launchDisplaceIdents;
    }
    
    /**
     * If set to true, all nodes will repel each other until no overlaps between 
     * nodes are left or the maximum amount of iterations has been reached.
     * default: true
     * @return boolean
     */
    public boolean getLaunchForcePush() {
        return this.launchForcePush;
    }
    
    /**
     * If set to true, node-pairs that have coordinates in close proximity to each 
     * other, will be aligned ad the mean coordinate between them.
     * default: true
     * @return boolean
     */
    public boolean getLaunchAlignNodes() {
        return this.launchAlignNodes;
    }
    
    /**
     * Get the maximum amount of iterations used by the force push step of the 
     * algorithm.
     * default: 500
     * @return boolean
     */
    public int getMaxiterations() {
        return this.maxiterations;
    }
    
    /**
     * Get the scaling parameter of the algorithm. 
     * Determines the distance between nodes.
     * default: 1.2
     * @return double
     */
    public double getScaling() {
        return this.scaling;
    }
    
    /**
     * Get the scaling factor that is used to scale subflow-nodes in the 
     * autoscaleNodes procedure.
     * default: 2.0
     * @return double
     */
    public double getSubflowscale() {
        return this.subflowscale;
    }
    
    /**
     * If set to true, debugging output will be printed in the command line and 
     * a second representation of the graph will be shown.
     * default: false
     * @return boolean
     */
    @Override
    public boolean getDebug() {
        return this.debug;
    }
    
    /**
     * Get the model graph to be laid out.
     * default: a graph is generated automatically from the workflow given.
     * @return DirectedGraph
     */
    public DirectedGraph getModelGraph() {
        return this.jgraph;
    }
    // </editor-fold>
    
    // <editor-fold desc="setter" defaultstate="collapsed">
    /**
     * Set the workflow to be laid out.
     * @param pworkflow VFlow.
     */
    @Override
    public void setWorkflow(VFlow pworkflow) {
        this.workflow = pworkflow;
    }
    
    /**
     * If set to true, the layout is applied to all subflows of the given 
     * workflow recursively.
     * default: true
     * @param precursive boolean
     */
    @Override
    public void setRecursive(boolean precursive) {
        this.recursive = precursive;
    }
    
    /**
     * If set to true, subflow nodes in the given workflow are automatically 
     * scaled to fit their contents.
     * default: true
     * @param pautoscaleNodes boolean
     */
    @Override
    public void setAutoscaleNodes(boolean pautoscaleNodes) {
        this.autoscaleNodes = pautoscaleNodes;
    }
    
    /**
     * Select the Jung layout that is supposed to be used in the first step of 
     * the algorithm.
     * 0 - ISOM Layout
     * 1 - FR Layout
     * 2 - KK Layout
     * 3 - DAG Layout (Does not terminate if the graph contains cycles)
     * default: 0
     * @param playoutSelector int
     */
    public void setLayoutSelector(int playoutSelector) {
        this.layoutSelector = playoutSelector;
    }
    
    /**
     * Set the aspect ratio of the initial drawing space of the graph.
     * Width of the drawing space is determined via the longest path in the 
     * graph.
     * Height is determined by dividing the width by the aspect ratio.
     * default: 16:9
     * @param paspectratio double
     */
    public void setAspectratio(double paspectratio) {
        this.aspectratio = paspectratio;
    }
    
    /**
     * If set to true, the layout is applied to a given Jung-Graph of types 
     * <VNode, Connection> instead of a VFlow.
     * Is used for sublayouts by separateDisjunctGraphs().
     * default: false
     * @param pjustgraph boolean
     */
    public void setJustgraph(boolean pjustgraph) {
        this.justgraph = pjustgraph;
    }
    
    /**
     * If set to true a depth-first-search is performed and all back edges are 
     * removed from the model graph. The layout is then applied without these 
     * edges.
     * Is only run if the graph contains cycles.
     * default: true 
     * @param plaunchRemoveCycles boolean
     */
    public void setLaunchRemoveCycles(boolean plaunchRemoveCycles) {
        this.launchRemoveCycles = plaunchRemoveCycles;
    }
    
    /**
     * If set to true disjunct parts of the model graph will be laid out 
     * separately and then arranged over each other to create the cumulative 
     * layout.
     * Is never run if launchRemoveCycles is false and the graph contains 
     * cycles, because it does not terminate in this case.
     * default: true
     * @param plaunchSeparateDisjunctGraphs boolean
     */
    public void setLaunchSeparateDisjunctGraphs(boolean plaunchSeparateDisjunctGraphs) {
        this.launchSeparateDisjunctGraphs = plaunchSeparateDisjunctGraphs;
    }
    
    /**
     * If set to true, the graph is rotated to reach an average flow direction 
     * parallel to the horizontal axis.
     * default: true
     * @param plaunchRotate boolean
     */
    public void setLaunchRotate(boolean plaunchRotate) {
        this.launchRotate = plaunchRotate;
    }
    
    /**
     * If set to true, nodes with an in-degree of 0 will be placed at the 
     * leftmost edge of the drawing space.
     * Is always run if launchSeparateDisjunctGraphs is true, launchRemoveCycles 
     * is false and the graph contains cycles, because separateDisjunctGraphs 
     * does not terminate if the graph contains cycles.
     * default: false
     * @param plaunchOrigin boolean
     */
    public void setLaunchOrigin(boolean plaunchOrigin) {
        this.launchOrigin = plaunchOrigin;
    }
    
    /**
     * If set to true, all nodes will be pushed to the right of their 
     * predecessors 
     * so no edge has a direction with a negative x-component.
     * (Does not terminate if the graph contains cycles)
     * default: true
     * @param plaunchPushBack boolean
     */
    public void setLaunchPushBack(boolean plaunchPushBack) {
        this.launchPushBack = plaunchPushBack;
    }
    
    /**
     * If set to true, nodes that are placed on the same coordinates will be 
     * displaced slightly, so the forcePush step can move them accordingly.
     * default: true
     * @param plaunchDisplaceIdents boolean
     */
    public void setLaunchDisplaceIdents(boolean plaunchDisplaceIdents) {
        this.launchDisplaceIdents = plaunchDisplaceIdents;
    }
    
    /**
     * Set true if all nodes shall be pushing each other away until no overlaps 
     * between nodes remain or the maximum amount of iterations has been 
     * reached.
     * default: true
     * @param plaunchForcePush boolean
     */
    public void setLaunchForcePush(boolean plaunchForcePush) {
        this.launchForcePush = plaunchForcePush;
    }
    
    /**
     * If set to true, node-pairs that have coordinates in close proximity to each 
     * other, will be aligned ad the mean coordinate between them.
     * default: true
     * @param plaunchAlignNodes boolean
     */
    public void setLaunchAlignNodes(boolean plaunchAlignNodes) {
        this.launchAlignNodes = plaunchAlignNodes;
    }
    
    /**
     * Set the maximum amount of iterations for the force push step of the 
     * algorithm.
     * default: 500
     * @param pmaxiterations int
     */
    public void setMaxiterations(int pmaxiterations) {
        this.maxiterations = pmaxiterations;
    }
    
    /**
     * Set the scaling parameter of the algorithm. 
     * Determines the distance between nodes.
     * default: 1.2
     * @param pscaling double
     */
    public void setScaling(double pscaling) {
        this.scaling = pscaling;
    }
    
    /**
     * Set the scaling factor that is used to scale subflow-nodes in the 
     * autoscaleNodes procedure.
     * default: 2.0
     * @param psubflowscale double
     */
    public void setSubflowscale(double psubflowscale) {
        this.subflowscale = psubflowscale;
    }
    
    /**
     * If set to true, debugging output will be printed in the command line and 
     * a second representation of the graph will be shown.
     * default: false
     * @param pdebug boolean
     */
    @Override
    public void setDebug(boolean pdebug) {
        this.debug = pdebug;
    }
    
    /**
     * Set the model graph to be laid out.
     * default: a graph is generated automatically from the workflow given.
     * @param pjgraph DirectedGraph
     */
    public void setModelGraph(DirectedGraph pjgraph) {
        this.jgraph = pjgraph;
    }
    // </editor-fold>
    
    /**
     * Sets up the model-fields. These include the nodearray, nodecount, origin-
     * nodes and the model graph.
     * Uses either a VFlow or a Jung-Graph depending on the justgraph parameter.
     * @return boolean
     */
    private boolean allNodesSetUp() {
        int i;
        if(!this.justgraph) {
            if(this.workflow == null) return false;
            // gather nodelist from workflow
            ObservableList<VNode> nodesTemp = this.workflow.getNodes();
            if(nodesTemp == null) return false;
            this.nodecount = nodesTemp.size();
            if(this.nodecount == 0) return false;
            this.nodes = new VNode[this.nodecount];
            
            // copy nodelist into nodearray for better performance in the future
            // and add nodes to the jgraph
            for(i = 0; i < this.nodecount; i++) {
                this.nodes[i] = nodesTemp.get(i);
                this.jgraph.addVertex(this.nodes[i]);
            }
        
            // get all edges
            this.conncount = 0;
            ObservableMap<String, Connections> allConnections = 
                    this.workflow.getAllConnections();
            // get all types of connections
            Set<String> keys = allConnections.keySet();
            Iterator<String> it = keys.iterator();
            while(it.hasNext()) {
                // get all connections for current type
                String currType = it.next();
                Connections currConns = allConnections.get(currType);
                ObservableList<Connection> connections =
                    currConns.getConnections();
                int currConnCount = connections.size();
                for(i = 0; i < currConnCount; i++) {
                    this.conncount++;
                    Connection currConn = connections.get(i);
                    // add an edge correspondig to the current connection to the 
                    // jgraph
                    VNode sender = 
                        this.nodes[getNodeID(currConn.getSender().getNode())];
                    VNode receiver = 
                        this.nodes[getNodeID(currConn.getReceiver().getNode())];
                    this.jgraph.addEdge(currConn, sender, receiver);
                }
            }
        }
        else {
            if(this.debug) System.out.println("laying out jgraph.");
            if(this.jgraph == null) return false;
            // gather nodelist from modelgraph
            Collection<VNode> temp = this.jgraph.getVertices();
            Iterator<VNode> it = temp.iterator();
            this.nodecount = temp.size();
            this.conncount = this.jgraph.getEdgeCount();
            this.nodes = new VNode[this.nodecount];
            // copy nodelist into nodearray for better performance in the future
            i = 0;
            while(it.hasNext()) {
                this.nodes[i] = it.next();
                i++;
            }
        }
        
        // get origin nodes
        this.origin = getOrigin();
        // check the graph for cycles
        this.cycle = checkCycles();
        return true;
    }
    
    /**
     * Gets all origin nodes of the graph.
     * Origin nodes are all nodes with an in-degree of 0.
     * @return Pair<Integer>[]: Array of Pairs. Each Pair contains the model-ID 
     * of the node and its successor count.
     */
    private Pair<Integer>[] getOrigin() {
        int i;
        // add all nodes with in-degree of 0 to list
        LinkedList<Integer> originL = new LinkedList<>();
        for(i = 0; i < this.nodecount; i++) {
            if(this.jgraph.inDegree(this.nodes[i]) == 0) originL.add(i);
        }
        // create array for origin values
        int length = originL.size();
        Pair<Integer>[] origina = new Pair[length];
        // add pairs of id and successorcount for all entries of the origin-list
        // to the origin array
        for(i = 0; i < length; i++) {
            int curr = originL.removeFirst();
            origina[i] = 
                new Pair<>(curr, this.jgraph.getSuccessorCount(this.nodes[curr]));
            if(this.debug) System.out.println(this.nodes[curr].getId() 
                    + " | In-Degree: " + this.jgraph.inDegree(this.nodes[curr]) 
                    + " Successors: " 
                    + this.jgraph.getSuccessorCount(this.nodes[curr]));
        }
        return origina;
    }

    /**
     * Calculates the average x and y coordinates of all nodes.
     * @return Point2D
     */
    private Point2D getGraphCenter() {
        int i;
        double centerx = 0;
        double centery = 0;
        for(i = 0; i < this.nodecount; i++) {
            Point2D currCoords = this.layout.transform(this.nodes[i]);
            centerx += currCoords.getX();
            centery += currCoords.getY();
        }
        centerx /= this.nodecount;
        centery /= this.nodecount;
        if(this.debug) System.out.println("Center of graph is at: (" 
                + centerx + "|" + centery + ")");
        return new Point2D.Double(centerx, centery);
    }
    
    /**
     * Checks if the graph contains cycles.
     * @return boolean
     */
    private boolean checkCycles() {
        int i;
        boolean[] checked = new boolean[this.nodecount];
        for(i = 0; i < this.nodecount; i++) {
            int j;
            // initialize checked with all entries set to false
            for(j = 0; j < this.nodecount; j++) {
                checked[j] = false;
            }
            // select start node
            VNode start = this.nodes[i];
            if(this.jgraph.getSuccessorCount(start) > 0) {
                Collection<VNode> succsnomod = this.jgraph.getSuccessors(start);
                LinkedList<VNode> succs = new LinkedList<>();
                succs.addAll(succsnomod);
                Iterator<VNode> it = succs.iterator();
                // iterate through all successors of start node
                while(it.hasNext()) {
                    VNode currNode = it.next();
                    // if current node is the start node, the start node can be 
                    // reached -> there is a cycle
                    if(currNode.equals(start)) {
                        if(this.debug) {
                            System.out.println("graph contains cycles.");
                        }
                        return true;
                    }
                    else {
                        // if current node is not the start node and has not yet 
                        // been checked, add all successors of current node to 
                        // the list, reset the iterator, set checked for current 
                        // node true.
                        // -> all nodes that are reacheable from start node will 
                        // be checked once.
                        if(!checked[getNodeID(currNode)]) {
                            succs.addAll(this.jgraph.getSuccessors(currNode));
                            it = succs.iterator();
                            checked[getNodeID(currNode)] = true;
                        }
                    }
                }
            }
            
        }
        if(this.debug) System.out.println("graph contains no cycles.");
        return false; 
    }
    
    /**
     * Performs a depth-first search and removes all back edges so that the 
     * resulting graph is cycle free.
     */
    private void removeCycles() {
        int i;
        boolean[] checked = new boolean[this.nodecount];
        // initialize checked with all entries set to false
        for(i = 0; i < this.nodecount; i++) {
            checked[i] = false;
        }
        LinkedList<VNode> path = new LinkedList<>();
        // iterate through all origin nodes
        for(i = 0; i < this.origin.length; i++) {
            // select current node as start node and start recursive search
            VNode start = this.nodes[this.origin[i].getFirst()];
            remCycR(start, path, checked);
        }
        for(i = 0; i < this.nodecount; i++) {
            // start recursive search for all nodes that have not yet been 
            // checked
            if(!checked[i]) {
                VNode start = this.nodes[i];
                remCycR(start, path, checked);
            }
        }
        this.cycle = false;
    }
    
    /**
     * Recursive helper-function to be used by removeCycles().
     * @param VNode curr: current node
     * @param path LinkedList<VNode>: the path from the root-node to the current 
     * node
     * @param checked boolean[]: array of length nodecount showing which nodes 
     * were already checked
     */
    private void remCycR(VNode curr, LinkedList<VNode> path, boolean[] checked) {
        if(!checked[getNodeID(curr)]) {
            // add current node to path
            path.addFirst(curr);
            checked[getNodeID(curr)] = true;
            // perform search on all successors.
            Collection<VNode> succs = this.jgraph.getSuccessors(curr);
            if(!succs.isEmpty()) {
                Iterator<VNode> it = succs.iterator();
                while(it.hasNext()) {
                    VNode currSucc = it.next();
                    // if edge from curr to currSucc is a back edge
                    if(path.contains(currSucc)) {
                        // remove all edges from curr to currSucc
                        Collection<Connection> conns = 
                                this.jgraph.findEdgeSet(curr, currSucc);
                        Iterator<Connection> its = conns.iterator();
                        while(its.hasNext()) {
                            this.jgraph.removeEdge(its.next());
                            this.conncount--;
                        }
                        if(this.debug) System.out.println("removing edge from " 
                                + curr.getId() + " to " + currSucc.getId());
                    }
                    // if edge from curr to currSucc is not a back edge
                    else {
                        // run recursive search on currSucc
                        remCycR(currSucc, path, checked);
                    }
                }
            }
            path.removeFirst();
        }
    }
    
    /**
     * Checks the graph for multiple disjunct parts.
     * Gives each node an ID depending on which disjunct part it belongs to.
     * Then applies the layout with the given parameters to each disjunct part.
     * Arranges the resulting graphs over each other to create the cumulative 
     * layout.
     */
    private void separateDisjunctGraphs() {
        if(this.debug) System.out.println("separating disjunct graphs");
        int currID;
        int maxID = -1;
        LinkedList<VNode> nextnodes = new LinkedList<>();
        int[] graphs = new int[this.nodecount];
        int i;
        int j;
        // initialize graphID for all nodes to -1
        for(i = 0; i < this.nodecount; i++) {
            graphs[i] = -1;
        }
        // iterate through all origin nodes
        for(i = 0; i < this.origin.length; i++) {
            // add all reachable nodes to same graphID
            currID = maxID + 1;
            nextnodes.add(this.nodes[this.origin[i].getFirst()]);
            while(!nextnodes.isEmpty()) {
                int currNode = getNodeID(nextnodes.removeFirst());
                if(graphs[currNode] == -1) {
                    // if node has not yet been added to a graph,
                    // it is added to the current graphID
                    graphs[currNode] = currID;
                }
                else {
                    // if node was already reached from other graphID,
                    // all nodes in this iteration are connected with that 
                    // ID as well
                    if(this.debug) System.out.println("currID " + currID 
                            + " graphid " + graphs[currNode] + "graph: " 
                            + Arrays.toString(graphs));
                    for(j = 0; j < this.nodecount; j++) {
                        if(graphs[j] == currID) graphs[j] = graphs[currNode];
                    }
                    currID = graphs[currNode];
                    if(this.debug) System.out.println("graph now: " 
                            + Arrays.toString(graphs));
                }
                // add successors of current node to queue, so all reachable 
                // nodes will be iterated over
                if(this.jgraph.getSuccessorCount(this.nodes[currNode]) != 0) {
                    Collection<VNode> succ = 
                            this.jgraph.getSuccessors(this.nodes[currNode]);
                    Iterator<VNode> it = succ.iterator();
                    while(it.hasNext()) {
                        VNode temp = it.next();
                        if(graphs[getNodeID(temp)] != currID) {
                            nextnodes.add(temp);
                        }
                    }
                }
            }
            // calculate new maxID
            for(j = 0; j < this.nodecount; j++) {
                if(graphs[j] > maxID) maxID = graphs[j];
            }
        }
        
        if(this.debug) {
            for(i = 0; i < this.nodecount; i++) {
                System.out.println(this.nodes[i].getId() + " belongs to graph " 
                        + graphs[i]);
            }
        }
        
        // initialize subgenerator for subgraphs
        LayoutGeneratorSmart subgen = new LayoutGeneratorSmart();
        subgen.setAspectratio(this.aspectratio);
        subgen.setAutoscaleNodes(false);
        subgen.setDebug(this.debug);
        // use justgraph parameter in this case
        subgen.setJustgraph(true);
        subgen.setLaunchAlignNodes(this.launchAlignNodes);
        subgen.setLaunchDisplaceIdents(this.launchDisplaceIdents);
        subgen.setLaunchForcePush(this.launchForcePush);
        subgen.setLaunchOrigin(this.launchOrigin);
        subgen.setLaunchPushBack(this.launchPushBack);
        subgen.setLaunchRemoveCycles(this.launchRemoveCycles);
        subgen.setLaunchRotate(this.launchRotate);
        subgen.setLaunchSeparateDisjunctGraphs(false);
        subgen.setLayoutSelector(this.layoutSelector);
        subgen.setMaxiterations(this.maxiterations);
        subgen.setRecursive(false);
        subgen.setScaling(this.scaling);
        double y = 0.;
        double x = 0.;
        double[] xpos = new double[this.nodecount];
        double[] ypos = new double[this.nodecount];
        
        // iterate over all found graphIDs
        for(i = 0; i <= maxID; i++) {
            if(this.debug) System.out.println("--- laying out subgraph with ID " 
                    + i);
            double minx = Double.POSITIVE_INFINITY;
            double miny = Double.POSITIVE_INFINITY;
            double maxy = Double.NEGATIVE_INFINITY;
            // create subgraph for nodes with current graphID
            DirectedGraph<VNode, Connection> subgraph = 
                    new DirectedSparseGraph<>();
            for(j = 0; j < this.nodecount; j++) {
                if(graphs[j] == i) {
                    if(this.debug) System.out.println(this.nodes[j].getId() 
                            + " belongs to the subgraph with ID " + i);
                    subgraph.addVertex(this.nodes[j]);
                    Collection<Connection> conns = 
                            this.jgraph.getIncidentEdges(this.nodes[j]);
                    Iterator<Connection> it = conns.iterator();
                    while(it.hasNext()) {
                        Connection currConn = it.next();
                        VNode sender = currConn.getSender().getNode();
                        VNode receiver = currConn.getReceiver().getNode();
                        subgraph.addEdge(currConn, sender, receiver);
                    }
                }
            }
            // apply layout to subgraph
            subgen.setModelGraph(subgraph);
            subgen.generateLayout();
            // find outermost coordinates of graph
            for(j = 0; j < this.nodecount; j++) {
                if(graphs[j] == i) {
                    if(this.nodes[j].getX() < minx) minx = this.nodes[j].getX();
                    if(this.nodes[j].getY() < miny) miny = this.nodes[j].getY();
                    if((this.nodes[j].getY() + this.nodes[j].getHeight()) > maxy) {
                        maxy = this.nodes[j].getY() + this.nodes[j].getHeight();
                    }
                }
            }
            if(this.debug) System.out.println("graphID: " + i + " minx: " 
                    + minx + " miny: " + miny + " maxy: " + maxy 
                    + " will be set to: (" + x + "|" + y + ")");
            // offset calculated node positions with maximum coordinates of 
            // previous subgraphs
            for(j = 0; j < this.nodecount; j++) {
                if(graphs[j] == i) {
                    double newx = x + this.nodes[j].getX() - minx;
                    double newy = (y - miny) + this.nodes[j].getY();
                    if(this.debug) System.out.println("changing position of " 
                            + this.nodes[j].getId() + " from (" 
                            + this.nodes[j].getX() + "|" + this.nodes[j].getY() 
                            + ") to (" + newx + "|" + newy + ")");
                    xpos[j] = newx;
                    ypos[j] = newy;
                }
            }
            y += (maxy - miny) * this.scaling;
        }
        // apply positions to nodes
        for(i = 0; i < this.nodecount; i++) {
                this.nodes[i].setX(xpos[i]);
                this.nodes[i].setY(ypos[i]);
                if(this.debug) System.out.println(this.nodes[i].getId() 
                        + " has final position: (" + xpos[i] + "|" + ypos[i] 
                        + ")");
        }
    }
    
    /**
     * Generates a Layout for the workflow or jung-graph given.
     */
    @Override
    public void generateLayout() {
        if(this.debug) System.out.println("Generating layout.");
        // setup and check for errors
        if(allNodesSetUp()) {
            if(this.cycle && this.launchRemoveCycles) {
                removeCycles();
                this.origin = getOrigin();
            }
            // apply layout to subflows
            if(this.recursive) {
                runSubflows();
            }
            // scale nodes according to their contents.
            if(this.autoscaleNodes) {
                autoscaleNodes();
            }
            if(this.launchSeparateDisjunctGraphs) {
                // only run if the graph contains no cycles
                if(!this.cycle) {
                    separateDisjunctGraphs();
                    return;
                }
                // run origin instead
                else {
                    this.launchOrigin = true;
                    if(this.debug) System.out.println("Graph contains cycles ->"
                            + " origin used instead of seperate disjunct graphs"
                            + ".");
                }
            }
            // create jung-layout
            switch(this.layoutSelector) {
                case 0: // ISOM Layout
                    this.layout = new ISOMLayout<>(this.jgraph);
                    break;
                case 1: // FR Layout
                    this.layout = new FRLayout<>(this.jgraph);
                    break;
                case 2: // KK Layout
                    this.layout = new KKLayout<>(this.jgraph);
                    break;
                case 3: // DAG Layout
                    if(!this.cycle) this.layout = new DAGLayout<>(this.jgraph);
                    else {
                        this.layout = new ISOMLayout<>(this.jgraph);
                        if(this.debug) System.out.println("Graph contains "
                                + "cycles -> ISOM Layout used instead of DAG" 
                                + " Layout.");
                    }
                    break;
                default:
                    this.layout = new ISOMLayout<>(this.jgraph);
                    break;
            }
            stepLayoutApply();
            this.graphcenter = getGraphCenter();
            if(this.launchRotate) stepRotate();
            if(this.launchOrigin) stepOrigin();
            if(this.launchPushBack) {
                if(!this.cycle) stepPushBack();
                else if(this.debug) System.out.println("Graph contains cycles " 
                        + "-> PushBack skipped.");
            }
            if(this.launchDisplaceIdents) displaceIdents();
            if(this.launchAlignNodes) {
                this.maxiterations /= 2;
                if(this.launchForcePush) forcePush();
                alignNodes();
                if(this.launchDisplaceIdents) displaceIdents();
                if(this.launchForcePush) forcePush();
            }
            else {
                if(this.launchForcePush) forcePush();
            }
            if(this.debug) {
                int i;
                for(i = 0; i < this.nodecount; i++) {
                    Point2D currPos = this.layout.transform(this.nodes[i]);
                    System.out.println(this.nodes[i].getId() + " has final "
                            + "position: (" + currPos.getX() + "|" 
                            + currPos.getY() + ")");
                }
            }
        }
        else {
            if(this.debug) System.out.println("Error on setup.");
        }
    }
    
    /**
     * Applies the layout with the same parameters to each subflow.
     */
    private void runSubflows() {
        // initialize sub generator with the same parameters
        LayoutGeneratorSmart subgen = new LayoutGeneratorSmart(false);
        subgen.setAspectratio(this.aspectratio);
        subgen.setAutoscaleNodes(this.autoscaleNodes);
        subgen.setRecursive(this.recursive);
        subgen.setScaling(this.scaling);
        subgen.setSubflowscale(this.subflowscale);
        subgen.setLayoutSelector(this.layoutSelector);
        subgen.setJustgraph(false);
        subgen.setLaunchRemoveCycles(this.launchRemoveCycles);
        subgen.setLaunchSeparateDisjunctGraphs(this.launchSeparateDisjunctGraphs);
        subgen.setLaunchRotate(this.launchRotate);
        subgen.setLaunchOrigin(this.launchOrigin);
        subgen.setLaunchPushBack(this.launchPushBack);
        subgen.setLaunchDisplaceIdents(this.launchDisplaceIdents);
        subgen.setLaunchForcePush(this.launchForcePush);
        subgen.setLaunchAlignNodes(this.launchAlignNodes);
        subgen.setMaxiterations(this.maxiterations);
        // apply layout to each subflow
        Collection<VFlow> subconts = this.workflow.getSubControllers();
        Iterator<VFlow> it = subconts.iterator();
        while(it.hasNext()) {
            VFlow subflow = it.next();
            subgen.setWorkflow(subflow);
            subgen.generateLayout();
        }
    }
    
    /**
     * Scales subflow-nodes according to their contents.
     */
    private void autoscaleNodes() {
        Collection<VFlow> subconts = this.workflow.getSubControllers();
        Iterator<VFlow> it = subconts.iterator();
        // iterate over all subflows
        while(it.hasNext()) {
            VFlow subflow = it.next();
            // get node representation of the current subflow
            VNode flownode = subflow.getModel();
            if(this.debug) System.out.println("Resizing subflow-node "
                    + flownode.getId());
            // get nodes from the subflow
            Collection<VNode> subnodes = subflow.getNodes();
            if(subnodes.isEmpty()) continue;
            Iterator<VNode> nodeit = subnodes.iterator();
            double minx = Double.POSITIVE_INFINITY;
            double maxx = Double.NEGATIVE_INFINITY;
            double miny = Double.POSITIVE_INFINITY;
            double maxy = Double.NEGATIVE_INFINITY;
            // iterate over all subflow nodes to get outermost coordinates of 
            // subflow
            while(nodeit.hasNext()) {
                VNode currNode = nodeit.next();
                double x = currNode.getX();
                double y = currNode.getY();
                if(this.debug) System.out.println(currNode.getId() + " has "
                        + "position (" + x + "|" + y + ")");
                if(minx > x) minx = x;
                if(maxx < (x + currNode.getWidth())) maxx = x 
                        + currNode.getWidth();
                if(miny > y) miny = y;
                if(maxy < (y + currNode.getHeight())) maxy = y
                        + currNode.getHeight();
            }
            if(this.debug) System.out.println("extreme positions of " 
                    + flownode.getId() + " are min: (" + minx + "|" + miny 
                    + ") max: (" + maxx + "|" + maxy + ")");
            // calculate size of subflow
            double width = maxx - minx;
            double height = maxy - miny;
            if(this.debug) System.out.println("Resizing subflow-node "
                    + flownode.getId() + " from size (" + flownode.getWidth()
                    + "|" + flownode.getHeight() + ") to size ("
                    + (width / subnodes.size() * this.subflowscale) + "|" 
                    + (height / subnodes.size() * this.subflowscale) + ")");
            // set size of node representing current subflow
            flownode.setWidth(width / subnodes.size() * this.subflowscale);
            flownode.setHeight(height / subnodes.size() * this.subflowscale);
        }
    }

    /**
     * Applies the chosen layout implemented in the Jung Framework.
     */
    private void stepLayoutApply() {
        if(this.debug) System.out.println("--- applying layout.");
        // find cumulative width of longest path
        int maxpath = (int) Math.round(findMaxPathWidth() / this.scaling);
        // calculate height from maxpath-width and aspectratio
        int y = (int) Math.round(maxpath / this.aspectratio);
        this.layout.setSize(new Dimension(maxpath, y));
        // set layout coordinates
        int i;
        for(i = 0; i < this.nodecount; i++) {
            Point2D coords;
            coords = this.layout.transform(this.nodes[i]);
            this.nodes[i].setX(coords.getX() - this.nodes[i].getWidth() / 2);
            this.nodes[i].setY(coords.getY() - this.nodes[i].getHeight() / 2);
            if(this.debug) System.out.println(this.nodes[i].getId() + " | X: " 
                    + coords.getX() + " Y: " + coords.getY());
        }
    }
    
    /**
     * Finds the cumulative width of all nodes on the longest path in the graph.
     * @return double
     */
    private double findMaxPathWidth() {
        int[] maxPathFollowing = new int[this.nodecount];
        int maxPath = 0;
        int maxPathIndex = 0;
        double maxPathWidth = 0.;
        LinkedList<Integer> fifo = new LinkedList<>();
        Collection<VNode> nodelist;
        Iterator<VNode> it;
        int i;
        // set maxPathFollowing for every node without successors to 0
        // also add these nodes to the queue
        for(i = 0; i < this.nodecount; i++) {
            if(this.jgraph.getSuccessorCount(this.nodes[i]) == 0) {
                maxPathFollowing[i] = 0;
                fifo.add(i);
            }
        }
        // iterate through the queue
        while(!fifo.isEmpty()) {
            Integer currNode = fifo.removeFirst();
            // iterate over all successors of the current node
            if(this.jgraph.getSuccessorCount(this.nodes[currNode]) != 0) {
                nodelist = this.jgraph.getSuccessors(this.nodes[currNode]);
                it = nodelist.iterator();
                while(it.hasNext()) {
                    Integer currSucc = getNodeID(it.next());
                    int tempFollowing = 1 + maxPathFollowing[currSucc];
                    // set maxPathFollowing of the current node to 
                    // 1 + the largest maxPathFollowing of all successors
                    if(tempFollowing > maxPathFollowing[currNode]) {
                        maxPathFollowing[currNode] = tempFollowing;
                    }
                }
            }
            // add all predecessors of the current node to the queue
            nodelist = this.jgraph.getPredecessors(this.nodes[currNode]);
            it = nodelist.iterator();
            while(it.hasNext()) {
                fifo.add(getNodeID(it.next()));
            }
        }
        // find max path and its start node
        for(i = 0; i < this.nodecount; i++) {
            if(maxPathFollowing[i] > maxPath) {
                maxPath = maxPathFollowing[i];
                maxPathIndex = i;
            }
        }
        // calculate width of max path
        maxPathWidth += this.nodes[maxPathIndex].getWidth();
        nodelist = this.jgraph.getSuccessors(this.nodes[maxPathIndex]);
        maxPath -= 1;
        it = nodelist.iterator();
        while(it.hasNext()) {
            VNode currSucc = it.next();
            if(maxPath == maxPathFollowing[getNodeID(currSucc)]) {
                maxPathWidth += this.nodes[getNodeID(currSucc)].getWidth();
                maxPath--;
                nodelist = this.jgraph.getSuccessors(currSucc);
                it = nodelist.iterator();
            }
        }
        return maxPathWidth;
    }
    
    /**
     * Rotates the entire graph around its center point, so its new average 
     * edge-direction is parallel to the horizontal axis from left to right.
     */
    private void stepRotate() {
        if(this.debug) System.out.println("--- starting rotation.");
        int i;
        if(this.conncount == 0) return;

        double centerx = this.graphcenter.getX();
        double centery = this.graphcenter.getY();
        if(this.debug) System.out.println("center of rotation: (" + centerx 
                + "|" + centery + ")");
        
        // get average direction of edges
        double avgdirx = 0;
        double avgdiry = 0;
        Collection<Connection> conns = this.jgraph.getEdges();
        Iterator<Connection> it = conns.iterator();
        // iterate over all connections
        while(it.hasNext()) {
            Connection currConn = it.next();
            Point2D sender = 
                    this.layout.transform(currConn.getSender().getNode());
            Point2D receiver = 
                    this.layout.transform(currConn.getReceiver().getNode());
            // direction of connection as vector
            double dirx = receiver.getX() - sender.getX();
            double diry = receiver.getY() - sender.getY();
            avgdirx += dirx;
            avgdiry += diry;
            if(this.debug) System.out.println("Edge from " 
                    + currConn.getSender().getNode().getId() + " to " 
                    + currConn.getReceiver().getNode().getId() 
                    + " has direction: (" + dirx + "|" +diry + ") = " 
                    + (diry / dirx));
        }
        avgdirx /= this.conncount;
        avgdiry /= this.conncount;
        double avghyp = Math.sqrt(Math.pow(avgdirx, 2.) 
                + Math.pow(avgdiry, 2.));
        if(this.debug) System.out.println("average horizontal vector: " 
                + avgdirx);
        if(this.debug) System.out.println("original average edge direction: " 
                + ((avgdiry / avgdirx) / this.conncount));
        // mirror graph at vertical axis through center point
        // if the horizontal component of the average edge direction 
        // is negative.
        if(avgdirx < 0) {
            for(i = 0; i < this.nodecount; i++) {
                Point2D currCoords = this.layout.transform(this.nodes[i]);
                double newx;
                newx = ((-1) * (currCoords.getX() - centerx)) + centerx;
                Point2D newCoords = new Point2D.Double(newx, currCoords.getY());
                this.nodes[i].setX(newx);
                this.layout.setLocation(this.nodes[i], newCoords);
            }
            avgdirx *= -1;
            if(this.debug) System.out.println("mirrored graph");
            stepRotate();
            return;
        }
        // rotate graph around center so new average direction is 0
        // ( -> x-direction)
        for(i = 0; i < this.nodecount; i++) {
            Point2D currCoords = this.layout.transform(this.nodes[i]);
            if(this.debug) System.out.println("Rotated Vertex " 
                    + this.nodes[i].getId() + " from (" + currCoords.getX() 
                    + "|" + currCoords.getY() + ")");
            // move coordinates to point of origin
            double x = currCoords.getX() - centerx;
            double y = currCoords.getY() - centery;
            // rotate coordinates around point of origin
            double newx = (x * avgdirx / avghyp) + (y * avgdiry / avghyp);
            double newy = (y * avgdirx / avghyp) - (x * avgdiry / avghyp);
            // move coordinates back to their original place
            newx += centerx;
            newy += centery;
            Point2D newCoords = new Point2D.Double(newx, newy);
            this.nodes[i].setX(newx);
            this.nodes[i].setY(newy);
            this.layout.setLocation(this.nodes[i], newCoords);
            if(this.debug) System.out.println("to (" + newx + "|" + newy + ")");
        }
        if(this.debug) System.out.println("new average edge direction: " 
                + getAvgDir());
    }
    
    /**
     * Returns the average direction of all edges in the graph relative to the 
     * horizontal axis.
     * @return double
     */
    private double getAvgDir() {
        double avgdirx = 0;
        double avgdiry = 0;
        Collection<Connection> conns = this.jgraph.getEdges();
        Iterator<Connection> it = conns.iterator();
        // iterate over all connections
        while(it.hasNext()) {
            Connection currConn = it.next();
            Point2D sender = 
                    this.layout.transform(currConn.getSender().getNode());
            Point2D receiver = 
                    this.layout.transform(currConn.getReceiver().getNode());
            // direction of connection as vector
            double dirx = receiver.getX() - sender.getX();
            double diry = receiver.getY() - sender.getY();
            avgdirx += dirx;
            avgdiry += diry;
            if(this.debug) System.out.println("Edge from " 
                    + currConn.getSender().getNode().getId() + " to " 
                    + currConn.getReceiver().getNode().getId() 
                    + " has direction: (" + dirx + "|" +diry + ") = " 
                    + (diry / dirx));
        }
        avgdirx /= this.conncount;
        avgdiry /= this.conncount;
        return Math.atan(avgdiry / avgdirx);
    }
    
    /**
     * Places all nodes without predecessors at the leftmost edge of the graph.
     */
    private void stepOrigin() {
        if(this.debug) System.out.println("--- starting origin");
        int i;
        // get lowest x coordinate, largest height and width in graph
        double minx = this.nodes[0].getX();
        double maxw = 0.;
        double maxh = 0.;
        for(i = 0; i < this.nodecount; i++) {
            if(this.nodes[i].getX() < minx) minx = this.nodes[i].getX();
            if(this.nodes[i].getWidth() > maxw) maxw =
                    this.nodes[i].getWidth();
            if(this.nodes[i].getHeight() > maxh) maxh =
                    this.nodes[i].getHeight();
        }
        // place origin nodes on lowest x coordinate
        double lastpos =
                this.graphcenter.getY() 
                - ((this.origin.length / 2) * maxh * this.scaling);
        for(i = 0; i < this.origin.length; i++) {
            Point2D coords = new Point2D.Double();
            coords.setLocation((minx - (this.scaling * maxw)), lastpos);
            lastpos += this.scaling * (maxh);
            VNode currNode = this.nodes[this.origin[i].getFirst()];
            this.layout.setLocation(currNode, coords);
            currNode.setX(coords.getX());
            currNode.setY(coords.getY());
        }
    }
    
    /**
     * Pushes all nodes to the right, if they were left of one of 
     * their predecessors.
     */
    private void stepPushBack() {
        if(this.debug) System.out.println("--- starting push back.");
        LinkedList<Integer> fifo = new LinkedList<>();
        int i;
        // enter all origin nodes into fifo
        for(i = 0; i < this.origin.length; i++) {
            fifo.add(this.origin[i].getFirst());
        }
        while(!fifo.isEmpty()) {
            i = fifo.removeFirst();
            // move node to the right if predecessor has larger 
            // or equal x-coordinate
            Collection<VNode> nodelist = 
                    this.jgraph.getPredecessors(this.nodes[i]);
            Iterator<VNode> it = nodelist.iterator();
            while(it.hasNext()) {
                VNode pred = it.next();
                double minpos = pred.getX() + (pred.getWidth() * this.scaling);
                if(this.nodes[i].getX() < minpos) {
                    this.nodes[i].setX(minpos);
                    Point2D coords = new Point2D.Double(this.nodes[i].getX(),
                            this.nodes[i].getY());
                    this.layout.setLocation(this.nodes[i], coords);
                }
            }
            if(this.debug) System.out.println("position of Node " 
                    + this.nodes[i].getId() + " after push back : (" 
                    + this.nodes[i].getX() + "|" + this.nodes[i].getY() + ")");
            // add successors of current node to the fifo
            nodelist = this.jgraph.getSuccessors(this.nodes[i]);
            it = nodelist.iterator();
            while(it.hasNext()) {
                fifo.add(getNodeID(it.next()));
            }
        }
    }
    
    /**
     * Displaces nodes that are in exactly the same location.
     */
    private void displaceIdents() {
        // iterate over all pairs of nodes
        int i;
        for(i = 0; i < this.nodecount; i++) {
            int j;
            for(j = 0; j < this.nodecount; j++) {
                if(i == j) continue;
                // if distance between nodes is 0
                if(getRealNodeDist(this.nodes[i], this.nodes[j]) == 0) {
                    Collection<VNode> succs = 
                            this.jgraph.getSuccessors(this.nodes[i]);
                    if(succs.contains(this.nodes[j])) {
                        // if j is successor to i, move j to the right
                        this.nodes[j].setX(this.nodes[j].getX() + this.scaling);
                    }
                    else {
                        succs = this.jgraph.getSuccessors(this.nodes[j]);
                        if(succs.contains(this.nodes[i])) {
                            // if i is successor to j, move i to the right
                            this.nodes[i].setX(this.nodes[i].getX() 
                                    + this.scaling);
                        }
                        else {
                            // if the nodes are not successors to each other,
                            // displace them vertically
                            this.nodes[i].setY(this.nodes[i].getY() 
                                    + this.scaling);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Applies force to each node, to push each other away and remove overlaps.
     */
    private void forcePush() {
        if(this.debug) System.out.println("--- starting force push");
        int iteration;
        Boolean change = true;
        // go through all iterations
        for(iteration = 0; iteration < this.maxiterations; iteration++) {
            if(this.debug) System.out.println("iteration: " + (iteration + 1) 
                    + " of " + this.maxiterations);
            if(!change) break;
            change = false;
            int i;
            // iterate over all pairs of nodes
            for(i = 0; i < this.nodecount; i++) {
                int j;
                for(j = 0; j < this.nodecount; j++) {
                    if(j == i) continue;
                    double realDist = getRealNodeDist(this.nodes[i],
                            this.nodes[j]);
                    double desDist = getDesiredNodeDist(this.nodes[i],
                            this.nodes[j]);
                    // if the desired distance is larger than the existing distance
                    if((realDist < desDist) && (realDist != 0)) {
                        change = true;
                        // midpoints of both nodes:
                        double x1 = this.nodes[i].getX() 
                                + (this.nodes[i].getWidth() / 2);
                        double y1 = this.nodes[i].getY() 
                                + (this.nodes[i].getHeight() / 2);
                        double x2 = this.nodes[j].getX() 
                                + (this.nodes[j].getWidth() / 2);
                        double y2 = this.nodes[j].getY() 
                                + (this.nodes[j].getHeight() / 2);
                        // vector between nodes:
                        double vx = x2 - x1;
                        double vy = y2 - y1;
                        // displacement factor:
                        double phi = (desDist - realDist) 
                                / Math.sqrt(Math.pow(vx, 2) + Math.pow(vy, 2));
                        // new positions of midpoints:
                        double xf = x2 + (vx * phi) + 1.;
                        double yf = y2 + (vy * phi) + 1.;
                        // displacement:
                        double newx = xf - (this.nodes[j].getWidth() / 2);
                        double newy = yf - (this.nodes[j].getHeight() / 2);
                        this.nodes[j].setX(newx);
                        this.nodes[j].setY(newy);
                        Point2D coords = new Point2D.Double(newx, newy);
                        this.layout.setLocation(this.nodes[j], coords);
                        if(this.debug) {
                            System.out.println(this.nodes[i].getId() 
                                    + " pushed " + this.nodes[j].getId() 
                                    + " from (" + x2 + "|" + y2 + ") to (" 
                                    + xf + "|" + yf + ")");
                            System.out.println("distances before -> real: " 
                                    + realDist + "; desired: " + desDist);
                            System.out.println("distances after -> real: " 
                                    + getRealNodeDist(this.nodes[i], 
                                            this.nodes[j]) + "; desired: " 
                                    + getDesiredNodeDist(this.nodes[i], 
                                            this.nodes[j]));
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Returns distance between center points of two nodes.
     * @param node1 VNode
     * @param node2 VNode
     * @return double
     */
    private double getRealNodeDist(VNode node1, VNode node2) {
        double distx = (node1.getX() + (node1.getWidth() / 2)) 
                - (node2.getX() + (node2.getWidth() / 2));
        double disty = (node1.getY() + (node1.getHeight() / 2)) 
                - (node2.getY() + (node2.getHeight() / 2));
        return Math.sqrt(Math.pow(distx, 2) + Math.pow(disty, 2));
    }

    /**
     * Returns the desired distance between the center points of two nodes.
     * @param node1 VNode
     * @param node2 VNode
     * @return double
     */
    private double getDesiredNodeDist(VNode node1, VNode node2) {
        // get coordinates of both nodes
        double w1 = node1.getWidth();
        double h1 = node1.getHeight();
        double x1 = node1.getX() + (w1 / 2);
        double y1 = node1.getY() + (h1 / 2);
        double w2 = node2.getWidth();
        double h2 = node2.getHeight();
        double x2 = node2.getX() + (w2 / 2);
        double y2 = node2.getY() + (h2 / 2);
        // calculate vector between node centerpoints
        double f1;
        double vx = x1 - x2;
        double vy = y1 - y2;
        // divide component of vector by half of the node-dimensions
        double xcomp = Math.abs(2 * vx / w1);
        double ycomp = Math.abs(2 * vy / h1);
        // determine if vector direction is closer to vertical or horizontal, 
        // relative to the diagonal of the node.
        if(xcomp >= ycomp) {
            // calculate distance between centerpoint and edge of node
            // horizontal case:
            f1 = w1 / (Math.cos(Math.atan(vy / vx)) * 2);
        }
        else {
            // calculate distance between centerpoint and edge of node
            // vertical case:
            f1 = h1 / (Math.cos(Math.atan(vx / vy)) * 2);
        }
        // repeat the same procedure with the second node
        double f2;
        xcomp = Math.abs(2 * vx / w2);
        ycomp = Math.abs(2 * vy / h2);
        if(xcomp >= ycomp) {
            f2 = w2 / (Math.cos(Math.atan(vy / vx)) * 2); 
        }
        else {
            f2 = h2 / (Math.cos(Math.atan(vx / vy)) * 2);
        }
        return (f1 + f2) * this.scaling;
    }
    
    /**
     * Aligns nodes pairwise with each other.
     */
    private void alignNodes() {
        if(this.debug) System.out.println("aligning nodes with similar "
                + "coordinates.");
        int i;
        int j;
        boolean change;
        // iterate through all pairs of nodes
        for(i = 0; i < this.nodecount; i++) {
            VNode n1 = this.nodes[i];
            double x1 = n1.getX();
            double y1 = n1.getY();
            double w1 = n1.getWidth();
            double h1 = n1.getHeight();
            for(j = 0; j < this.nodecount; j++) {
                if(i != j) {
                    VNode n2 = this.nodes[j];
                    double x2 = n2.getX();
                    double y2 = n2.getY();
                    double w2 = n2.getWidth();
                    double h2 = n2.getHeight();
                    double threshold;
                    // horizontal alignment
                    if(x1 != x2) {
                        threshold = (w1 + w2) * (this.scaling - 1) / 2;
                        change = false;
                        // align by left side
                        if(Math.abs(x1 - x2) < threshold) {
                            change = true;
                            n1.setX((x1 + x2) / 2);
                            n2.setX((x1 + x2) / 2);
                        }
                        // align by right side
                        else if(Math.abs((x1 + w1) - (x2 + w2)) < threshold) {
                            change = true;
                            n1.setX(((x1 + w1 + x2 + w2) / 2) - w1);
                            n2.setX(((x1 + w1 + x2 + w2) / 2) - w2);
                        }
                        // align by center
                        else if(Math.abs((x1 + (w1 / 2)) - (x2 + (w2 / 2))) < threshold) {
                            change = true;
                            n1.setX((((2 * x1) + w1 + (2 * x2) + w2) / 4) - (w1 / 2));
                            n2.setX((((2 * x1) + w1 + (2 * x2) + w2) / 4) - (w2 / 2));
                        }
                        if((this.debug) && (change)) System.out.println(n1.getId() 
                                + " and " + n2.getId() + " have been aligned at"
                                + " x coordinate " + n1.getX());
                    }
                    // vertical alignment
                    if(y1 != y2) {
                        threshold = (h1 + h2) * (this.scaling - 1) / 2;
                        change = false;
                        // align by top side
                        if(Math.abs(y1 - y2) < threshold) {
                            change = true;
                            n1.setY((y1 + y2) / 2);
                            n2.setY((y1 + y2) / 2);
                        }
                        // align by bottom side
                        else if(Math.abs((y1 + h1) - (y2 + h2)) < threshold) {
                            change = true;
                            n1.setY(((y1 + h1 + y2 + h2) / 2) - h1);
                            n2.setY(((y1 + h1 + y2 + h2) / 2) - h2);
                        }
                        // align by center
                        else if(Math.abs((y1 + (h1 / 2)) - (y2 - (h2 / 2))) < threshold) {
                            change = true;
                            n1.setY((((2 * y1) + h1 + (2 * y2) + h2) / 4) - (h1 / 2));
                            n2.setY((((2 * y1) + h1 + (2 * y2) + h2) / 4) - (h2 / 2));
                        }
                        if((this.debug) && (change)) System.out.println(n1.getId() 
                                + " and " + n2.getId() + " have been aligned at"
                                + " y coordinate " + n1.getY());
                    }
                }
            }
        }
    }
    
    /**
     * Returns index of the given node in the nodearray.
     * @param pnode VNode
     * @return Integer
     */
    private Integer getNodeID(VNode pnode) {
        int i;
        for(i = 0; i < this.nodecount; i++) {
            if(this.nodes[i].equals(pnode))
                return i;
        }
        return -1;
    }
}