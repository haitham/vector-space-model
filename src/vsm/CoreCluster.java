package vsm;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class CoreCluster extends Cluster {
	
	private Core core;
	
	public CoreCluster(Core core){
		this.core = core;
	}

	@Override
	protected Double calculateSimilarity(Cluster cluster) {
		throw new NotImplementedException();
	}

	public Double getSimilarity(Document document) {
		return this.core.getDocument().getSimilarity(document);
	}

}
