package vsm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

@SuppressWarnings("deprecation")
public class Document {
	
	private File file;
	private int termSize;
	private HashMap<Term, Integer> termFrequencies;
	private HashMap<Term, Double> termScores;
	private int senseSize;
	private HashMap<WordNetSense, Integer> senseFrequencies;
	private HashMap<WordNetSense, Double> senseScores;
//	private HashMap<Document, Double> similarities;
	private HashMap<Document, Double> termSimilarities;
	private HashMap<Document, Double> senseSimilarities;
	private Node node;
	private HashMap<Double, Integer> neighborhoodSizes;
	
	private static List<Document> allDocuments;
	public static File stop_words;
	private static String conceptLevel;
	private static Double conceptAlpha;
	private static String wsdType;
	private static Integer wsdContext;
	
	public static boolean includeSynonyms(){
		return("synonyms".equals(conceptLevel) || "hypernyms".equals(conceptLevel));
	}
	
	public static boolean includeHypernyms(){
		return("hypernyms".equals(conceptLevel));
	}
	
	public static boolean includeWsd(){
		return (!"none".equals(wsdType));
	}
	
	public static boolean wsdPrimitive(){
		return ("primitive".equals(wsdType) || "expanded_primitive".equals(wsdType)) || "simplified_lesk".equals(wsdType) || ("simplified_lesk_plus".equals(wsdType));
	}
	
	public static boolean wsdExpandedPrimitive(){
		return ("expanded_primitive".equals(wsdType));
	}
	
	public static boolean wsdSimplifiedLesk(){
		return ("simplified_lesk".equals(wsdType)) || ("simplified_lesk_plus".equals(wsdType));
	}
	
	public static boolean wsdSimplifiedLeskPlus(){
		return ("simplified_lesk_plus".equals(wsdType));
	}
	
	public Document(File file){
		this.file = file;
		this.termSize = 0;
		termFrequencies = new HashMap<Term, Integer>();
		termScores = new HashMap<Term, Double>();
//		similarities = new HashMap<Document, Double>();
		termSimilarities = new HashMap<Document, Double>();
		if (includeSynonyms()){
			this.senseSize = 0;
			senseFrequencies = new HashMap<WordNetSense, Integer>();
			senseScores = new HashMap<WordNetSense, Double>();
			senseSimilarities = new HashMap<Document, Double>();
		}
		node = new Node();
		neighborhoodSizes = new HashMap<Double, Integer>();
		analyze();
	}
	
	private Document(List<Document> aggregate){
		termScores = new HashMap<Term, Double>();
//		similarities = new HashMap<Document, Double>();
		termSimilarities = new HashMap<Document, Double>();
		this.termSize = 0;
		Iterator<Term> termsIterator = Term.getAllTerms().iterator();
		Term term;
		Double scoresSum;
		Iterator<Document> docsIterator;
		while (termsIterator.hasNext()){
			term = termsIterator.next();
			docsIterator = aggregate.iterator();
			scoresSum = 0.0;
			while (docsIterator.hasNext()){
				scoresSum += docsIterator.next().getScore(term);
			}
			if (scoresSum > 0.0001){
				termSize ++;
				termScores.put(term, scoresSum/aggregate.size());
			}
		}
		if (includeSynonyms()){
			senseFrequencies = new HashMap<WordNetSense, Integer>();
			senseScores = new HashMap<WordNetSense, Double>();
			senseSimilarities = new HashMap<Document, Double>();
			this.senseSize = 0;
			Iterator<WordNetSense> sensesIterator = WordNetSense.getAllSenses().iterator();
			WordNetSense sense;
			while (sensesIterator.hasNext()){
				sense = sensesIterator.next();
				docsIterator = aggregate.iterator();
				scoresSum = 0.0;
				while (docsIterator.hasNext()){
					scoresSum += docsIterator.next().getScore(sense);
				}
				if (scoresSum > 0.0001){
					senseSize ++;
					senseScores.put(sense, scoresSum/aggregate.size());
				}
			}
		}
	}
	
	public String getFileName(){
		return file.getName();
	}
	
	public File getFile(){
		return file;
	}
	
	public static List<Document> getAllDocuments(){
		return allDocuments;
	}
	
	public Double getScore(Term term){
		Double score = termScores.get(term);
		if (score == null){
			return 0.0;
		}
		return score;
	}
	
	public Double getScore(WordNetSense sense){
		Double score = senseScores.get(sense);
		if (score == null){
			return 0.0;
		}
		return score;
	}
	
	private void analyze(){
		try {
			StandardAnalyzer analyzer = new StandardAnalyzer(stop_words);
			TokenStream stream = analyzer.tokenStream("text", new FileReader(file));
			Token token = stream.next();
			while ( token != null ){
				addToken(token);
				token = stream.next();
			}
			addSensesIfIncluded(termFrequencies.keySet());
		} catch (FileNotFoundException e) {
			System.out.println("Failed to load document: " + file.getName());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Error tokenizing document: " + file.getName());
			e.printStackTrace();
		}
	}
	
	private Integer getOverlap(List<String> senseGlossTerms, Term docTerm, WordNetSense docTermSense){
		Integer overlap = 0;
		//for each gloss term of the tested sense
		for (String glossTerm : senseGlossTerms){
			// match: context term == a gloss term of the tested sense
			if (docTerm.getValue().equals(glossTerm)){
				if (wsdSimplifiedLeskPlus()){
					overlap += 4;
				} else {
					overlap ++;
				}
			}
			List<String> docTermSenseGlossTerms = docTermSense.getGlossTerms();
			// for each term of the sense of the context term
			for (String docTermSenseGlossTerm : docTermSenseGlossTerms){
				// match: a gloss term of a sense of a context term == a gloss term of the tested sense
				if (docTermSenseGlossTerm.equals(glossTerm)){
					overlap ++;
				}
			}
		}
		return overlap;
	}
	
	private List<WordNetSense> refineSenses(Term term, List<WordNetSense> senses, HashMap<Term, List<WordNetSense>> termSenses){
		List<WordNetSense> selectedSenses = new ArrayList<WordNetSense>();
		if (wsdPrimitive()){
			Integer maxMatch = -1;
			// for each sense of these to be filtered
			for (WordNetSense sense : senses){
				List<String> glossTerms = sense.getGlossTerms();
				Integer match = 0;
				// for each term in the context
				for (Term docTerm : termSenses.keySet()){
					// ignore congruent terms
					if (term.getValue().equals(docTerm.getValue())){
						continue;
					}
					if (wsdSimplifiedLesk()){
						Integer maxOverlap = -1;
						// for each sense of the context term
						for (WordNetSense docTermSense : termSenses.get(docTerm)){
							Integer overlap = getOverlap(glossTerms, docTerm, docTermSense);
							if (overlap > maxOverlap){
								maxOverlap = overlap;
							}
						}
						match += maxOverlap;
					} else {
						//for each gloss term of the tested sense
						for (String glossTerm : glossTerms){
							// match: context term == a gloss term of the tested sense
							if (docTerm.getValue().equals(glossTerm)){
								match ++;
							}
							if (wsdExpandedPrimitive()){
								// for each sense of the context term
								for (WordNetSense docTermSense : termSenses.get(docTerm)){
									List<String> docTermSenseGlossTerms = docTermSense.getGlossTerms();
									// for each term of the sense of the context term
									for (String docTermSenseGlossTerm : docTermSenseGlossTerms){
										// match: a gloss term of a sense of a context term == a gloss term of the tested sense
										if (docTermSenseGlossTerm.equals(glossTerm)){
											match ++;
										}
									}
								}
							}
						}
					}
				}
				if (match == maxMatch){
					selectedSenses.add(sense);
				} else if (match > maxMatch){
					maxMatch = match;
					selectedSenses = new ArrayList<WordNetSense>();
					selectedSenses.add(sense);
				}
			}
		}
		return selectedSenses;
	}
	
	private void addSensesIfIncluded(Set<Term> docTerms){
		if (includeSynonyms()){
			HashMap<Term, List<WordNetSense>> termSenses = new HashMap<Term, List<WordNetSense>>();
			for (Term term : docTerms){
				termSenses.put(term, WordNetSense.getSenses(term.getValue()));
			}
			for (Term term : docTerms){
				List<WordNetSense> senses = termSenses.get(term);
				
				if (includeWsd()){
					senses = refineSenses(term, senses, termSenses);
				}
				
				senseSize += senses.size();
				for (WordNetSense sense : senses){
					Integer senseFrequency = senseFrequencies.get(sense);
					if ( senseFrequency == null ){
						senseFrequencies.put(sense, 1);
						sense.addDocument(this);
					}else{
						senseFrequencies.put(sense, senseFrequency + 1);
					}
					if (includeHypernyms()){
						for (WordNetSense hyperHypoSense : sense.getHyperHypoSenses()){
							senseFrequency = senseFrequencies.get(hyperHypoSense);
							if ( senseFrequency == null ){
								senseFrequencies.put(hyperHypoSense, 1);
								hyperHypoSense.addDocument(this);
							}else{
								senseFrequencies.put(hyperHypoSense, senseFrequency + 1);
							}
						}
					}
				}
			}
		}
	}
	
	private void addToken(Token token){
		termSize ++;
		Term term = Term.getTerm(token.term());
		Integer termFrequency = termFrequencies.get(term);
		if ( termFrequency == null ){
			termFrequencies.put(term, 1);
			term.addDocument(this);
		}else{
			termFrequencies.put(term, termFrequency + 1);
		}
	}
	
	private Double tfIdf(Term term){
		Integer frequency = termFrequencies.get(term);
		Double tf = new Double(frequency) / termSize;
		Double idf = Math.log(new Double(1+allDocuments.size()) / (1+term.documentFrequency()));
		return tf * idf;
	}
	
	private Double tfIdf(WordNetSense sense){
		Integer frequency = senseFrequencies.get(sense);
		Double tf = new Double(frequency) / senseSize;
		Double idf = Math.log(new Double(1+allDocuments.size()) / (1+sense.documentFrequency()));
		return tf * idf;
	}
	
	private Double cosineSimilarity(Document doc){
		Double sim;
//		sim = similarities.get(doc);
//		if (sim == null){
			if (includeSynonyms()){
				sim = conceptAlpha*sensesCosineSimilarity(doc) + (1.0 - conceptAlpha)*termsCosineSimilarity(doc);
			} else {
				sim = termsCosineSimilarity(doc);
			}
//			similarities.put(doc, sim);
//			doc.similarities.put(this, sim);
//		}
		return sim;
	}
	
	private Double termsCosineSimilarity(Document doc){
		Double sim;
		sim = termSimilarities.get(doc);
		if (sim == null){
			sim = this.termsDotProduct(doc) / (this.termsMagnitude() * doc.termsMagnitude());
			termSimilarities.put(doc, sim);
			doc.termSimilarities.put(this, sim);
		}
		return sim;
	}
	
	private Double sensesCosineSimilarity(Document doc){
		Double sim;
		sim = senseSimilarities.get(doc);
		if (sim == null){
			sim = this.sensesDotProduct(doc) / (this.sensesMagnitude() * doc.sensesMagnitude());
			senseSimilarities.put(doc, sim);
			doc.senseSimilarities.put(this, sim);
		}
		return sim;
	}
	
	private Double termsDotProduct(Document doc){
		Iterator<Term> termsIterator = Term.getAllTerms().iterator();
		Double product = 0.0;
		while ( termsIterator.hasNext() ){
			Term term = termsIterator.next();
			product = product + this.getScore(term) * doc.getScore(term);
		}
		return product;
	}
	
	private Double sensesDotProduct(Document doc){
		Iterator<WordNetSense> sensesIterator = WordNetSense.getAllSenses().iterator();
		Double product = 0.0;
		while ( sensesIterator.hasNext() ){
			WordNetSense sense = sensesIterator.next();
			product = product + this.getScore(sense) * doc.getScore(sense);
		}
		return product;
	}
	
	private Double termsMagnitude(){
		return Math.sqrt(termsDotProduct(this));
	}
	
	private Double sensesMagnitude(){
		return Math.sqrt(sensesDotProduct(this));
	}
	
	public Double magnitude(){
		return termsMagnitude();
	}
	
	private void calculateScores(){
		Iterator<Term> termsIterator = termFrequencies.keySet().iterator();
		while ( termsIterator.hasNext() ){
			Term term = termsIterator.next();
			termScores.put(term, tfIdf(term));
		}
		if (includeSynonyms()){
			Iterator<WordNetSense> sensesIterator = senseFrequencies.keySet().iterator();
			while ( sensesIterator.hasNext() ){
				WordNetSense sense = sensesIterator.next();
				senseScores.put(sense, tfIdf(sense));
			}
		}
	}
	
	public static void loadDocuments(String path, String stopWordsPath, String desiredConceptLevel, Double desiredConceptAlpha, String desiredWsdType, Integer desiredWsdContext){
		stop_words = new File(stopWordsPath);
		allDocuments = new ArrayList<Document>();
		conceptLevel = desiredConceptLevel;
		conceptAlpha = desiredConceptAlpha;
		wsdType = desiredWsdType;
		wsdContext = desiredWsdContext;
		Iterator<File> iterator = Arrays.asList(new File(path).listFiles()).iterator();
		int filesCounter=0;
		while ( iterator.hasNext() ){
			filesCounter ++;
			System.out.println("loading doc:" + filesCounter);
			allDocuments.add(new Document(iterator.next()));
		}
		calculateAllScores();
		System.out.println("Documents loaded");
	}
	
	private static void calculateAllScores(){
		Iterator<Document> docsIterator = allDocuments.iterator();
		while ( docsIterator.hasNext() ){
			docsIterator.next().calculateScores();
		}
	}
	
	public Double getSimilarity(Document doc){
		return cosineSimilarity(doc);
	}
	
	public boolean isEquivalent(Document doc){
		return this.termsCosineSimilarity(doc) > 0.9999 && this.termsMagnitude() - doc.termsMagnitude() < 0.0001;
	}
	
	public static Document mean(List<Document> documents){
		return new Document(documents);
	}
	
	public static void resetAllSimilarities(Double desiredConceptAlpha){
		resetConceptSetting(desiredConceptAlpha);
		for (Document doc : allDocuments){
			doc.resetSimilarities();
		}
	}
	
	public static void resetConceptSetting(Double desiredConceptAlpha){
		conceptAlpha = desiredConceptAlpha;
//		conceptLevel = desiredConceptLevel;
	}
	
	public void resetSimilarities(){
		termSimilarities = new HashMap<Document, Double>();
		senseSimilarities = new HashMap<Document, Double>();
//		similarities = new HashMap<Document, Double>();
		neighborhoodSizes = new HashMap<Double, Integer>();
	}
	
	public void visit(){
		node.visited = true;
	}
	
	public void markAsNoise(){
		node.noise = true;
	}
	
	public void markAsClustered(){
		node.clustered = true;
	}
	
	public boolean isVisited(){
		return node.visited;
	}
	
	public boolean isNoise(){
		return node.noise;
	}
	
	public boolean isClustered(){
		return node.clustered;
	}
	
	public Integer getNeighborHoodSize(Double neighborhood){
		Integer size = neighborhoodSizes.get(neighborhood);
		if (size == null){
			size = calculateNeighborhoodSize(neighborhood);
			neighborhoodSizes.put(neighborhood, size);
		}
		return size;
	}
	
	
	private Integer calculateNeighborhoodSize(Double neighborhood) {
		Integer size = 0;
		for (Iterator<Document> docsIterator = allDocuments.iterator(); docsIterator.hasNext();){
			Document doc = docsIterator.next();
			if (doc == this)
				continue;
			if (getSimilarity(doc) > neighborhood)
				size ++;
		}
		return size;
	}

	public String toString(){
		Iterator<Term> termsIterator = termFrequencies.keySet().iterator();
		StringBuilder result = new StringBuilder(file.getName()).append(": ").append(termSize).append(" words\n");
		Term term;
		while ( termsIterator.hasNext() ){
			term = termsIterator.next();
			result.append(term.getValue()).append(": frequency(").append(termFrequencies.get(term).toString()).append("), score(").append(termScores.get(term).toString()).append(")\n");
		}
		return result.append("\n").toString();
	}
	
	public static String allToString(){
		Iterator<Document> docsIterator = allDocuments.iterator();
		StringBuilder result = new StringBuilder("");
		while ( docsIterator.hasNext() ){
			result.append(docsIterator.next());
		}
		return result.toString();
	}
	
	public static void printAllSimilarities(){
		Iterator<Document> docsIterator = allDocuments.iterator();
		while ( docsIterator.hasNext() ){
			Document doc = docsIterator.next();
			System.out.print(doc.file.getName() + ": ");
			Iterator<Document> othersIterator = allDocuments.iterator();
			while (othersIterator.hasNext() ){
				Document other = othersIterator.next();
				if ( doc == other ){ continue; }
				System.out.print("(" + other.file.getName() + ": " + doc.getSimilarity(other) + ")");
			}
			System.out.println();
		}
	}

	public void resetNode() {
		node = new Node();
	}

	public static Double getConceptAlpha() {
		return conceptAlpha;
	}
	
}
