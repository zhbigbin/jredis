/*
 *   Copyright 2009-2010 Joubin Houshyar
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *    
 *   http://www.apache.org/licenses/LICENSE-2.0
 *    
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.jredis.ri.cluster.ketama;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jredis.ProviderException;
import org.jredis.cluster.ClusterModel;
import org.jredis.cluster.ClusterNodeSpec;
import org.jredis.cluster.ClusterSpec;
import org.jredis.cluster.ClusterType;
import org.jredis.cluster.model.ClusterNodeMap;
import org.jredis.ri.alphazero.support.Log;
import org.jredis.ri.cluster.support.CryptoHashUtils;

/**
 * [TODO: document me!]
 *
 * @author  joubin (alphazero@sensesay.net)
 * @date    Mar 29, 2010
 * 
 */

public class KetamaClusterModel extends ClusterModel.Support implements ClusterModel {

	// ------------------------------------------------------------------------
	// Properties
	// ------------------------------------------------------------------------
	
	/** what is a sensible value here? */
	private static final double REPLICATION_CONST = 10;
	/**  */
	private ClusterNodeMap	nodeMap;
	/**  */
	private KetamaHashProvider ketamaHashAlgo;
	/**  */
	private int nodeReplicationCnt;
	
	// ------------------------------------------------------------------------
	// Properties
	// ------------------------------------------------------------------------
	
	/** 
	 * Instantiate and initialize the node mape of a Ketama-based {@link ClusterModel}.
     * @param clusterSpec
     */
    public KetamaClusterModel (ClusterSpec clusterSpec) {
	    super(clusterSpec);
    }

	// ------------------------------------------------------------------------
	// Interface
	// ------------------------------------------------------------------------
    
	/* (non-Javadoc) @see org.jredis.cluster.ClusterModel#getNodeForKey(byte[]) */
	public ClusterNodeSpec getNodeForKey (byte[] key) {
		long hash = ketamaHashAlgo.hash(key);
		final ClusterNodeSpec rv;
		if(!nodeMap.containsKey(hash)) {
			// Java 1.6 adds a ceilingKey method, but I'm still stuck in 1.5
			// in a lot of places, so I'm doing this myself.
			SortedMap<Long, ClusterNodeSpec> tailMap=nodeMap.tailMap(hash);
			if(tailMap.isEmpty()) {
				hash = nodeMap.firstKey();
			} 
			else {
				hash=tailMap.firstKey();
			}
		}
		rv = nodeMap.get(hash);
		return rv;
	}
	
	/* (non-Javadoc) @see org.jredis.cluster.ClusterModel#supportsReconfiguration() */
    public boolean supportsReconfiguration () {
	    return false;
    }
    
    /* (non-Javadoc) @see org.jredis.cluster.ClusterModel#supports(org.jredis.cluster.ClusterType) */
    final public boolean supports(ClusterType clusterType){
    	return clusterType == ClusterType.CONSISTENT_HASH;
    }
    // ------------------------------------------------------------------------
    // Inner Ops
    // ------------------------------------------------------------------------
    
	/**
	 * Per original paper on consistent hashing, the replication count of any given bucket is
	 * k*log(C), where C is the number of buckets (i.e. nodes).  We're using {@link KetamaNodeMapper#REPLICATION_CONST}
	 * as k.
	 * 
     * @param nodeCnt number of server nodes ("buckets" per original paper) in the Ketama cluster
     * @return
     */
    static public int replicationCount(int nodeCnt){
    	return (int) (Math.log(nodeCnt) * REPLICATION_CONST);    	
    }

	/* (non-Javadoc) @see org.jredis.cluster.ClusterModel.Support#initializeModel() */
    @Override
    protected void initializeModel () {
    	this.ketamaHashAlgo = new KetamaHashProvider();
		this.nodeMap = new NodeMap();
    	nodeReplicationCnt = replicationCount(clusterSpec.getNodeSpecs().size());
    	mapNodes();
    }

	/**
	 * This method is a slightly modified version of net.spy.memcached.KetamaNodeLocator's constructor.
	 * @see <a href="http://github.com/????????/">GIT HUB LINK HERE ...</a>
	 */

	private void mapNodes () 
	{		
		try {
			Set<ClusterNodeSpec> 	nodes = clusterSpec.getNodeSpecs();
			for(ClusterNodeSpec node : nodes) {
				mapNode(node);
			}
			if(nodeMap.size() != (nodeReplicationCnt/4) * nodes.size() * 4) {
				Log.error("nodeMap size: " + nodeMap.size() + " | expected: " + nodeReplicationCnt * nodes.size());
				throw new ProviderException ("[BUG]: expecting node map size to be multiple of replication count * cluster node count");
			}
		}
		catch (ClassCastException e) {
			throw new ProviderException ("[BUG] KetamaNodeMappingAlgorithm requires a KetamaHashAlgorithm");
		}
	}

	private boolean mapNode(ClusterNodeSpec node){
		// Dustin says: "Ketama does some special work with md5 where it reuses chunks."
		for(int i=0; i<nodeReplicationCnt / 4; i++) {
			byte[] digest;
			digest = CryptoHashUtils.computeMd5(node.getKeyForReplicationInstance(i));
			for(int h=0;h<4;h++) {
				// Joubin says: here's we're calling a KetamaHashProvider specific method that does the 
				// Ketama chunking per above.  
				nodeMap.put(ketamaHashAlgo.hash(digest, h), node);
			}
		}
		return false;
	}
    
	/* (non-Javadoc) @see org.jredis.cluster.ClusterModel.Support#onNodeAddition(org.jredis.cluster.ClusterNodeSpec) */
    @Override
    protected boolean onNodeAddition (ClusterNodeSpec newNode) {
    	throw new ProviderException("[BUG] basic KetamaClusterModel does NOT support reconfiguration of nodes");
    }

	/* (non-Javadoc) @see org.jredis.cluster.ClusterModel.Support#onNodeRemoval(org.jredis.cluster.ClusterNodeSpec) */
    @Override
    protected boolean onNodeRemoval (ClusterNodeSpec newNode) {
    	throw new ProviderException("[BUG] basic KetamaClusterModel does NOT support reconfiguration of nodes");
    }

    
    // ========================================================================
    // Inner Types
    // ========================================================================
    
	// ------------------------------------------------------------------------
	// ClusterNodeMap impl.
	// ------------------------------------------------------------------------
	
    @SuppressWarnings("serial")
//    public static class NodeMap extends TreeMap<Long, ClusterNodeSpec> implements SortedMap<Long, ClusterNodeSpec>{
    public class NodeMap extends TreeMap<Long, ClusterNodeSpec> implements ClusterNodeMap{
    	
    }
}
