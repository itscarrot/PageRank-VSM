package websearching;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

public class PageRankVSM {
	static Map<String, Integer> urlMap = new HashMap<String, Integer>();
	static double[] score = new double[10000];
	static double[] vec = new double[10000];

	static double alpha = 0.1;

	// transition probability matrix
	static double[][] prob = new double[10000][10000];

	public static void main(String[] args) throws IOException, ParseException {
		// TODO Auto-generated method stub

		queryScore();
		indexURL();
		constructMatrix();

		for (int i = 0; i < 10000; i++) {
			vec[i] = 1 / 10000.0;
		}


		double[][] prob1 = new double[vec.length][vec.length];

		for (int i = 0; i < prob[0].length; i++) {
			double norm = 0;
			for (int j = 0; j < prob[i].length; j++) {
				norm += prob[i][j];
			}
			if (norm != 0) {
				for (int j = 0; j < prob[i].length; j++) {
					prob1[i][j] = prob[i][j] / norm;
					prob1[i][j] *= (1 - alpha);
					prob1[i][j] += alpha * score[j];
				}
			} else {
				for (int j = 0; j < prob[i].length; j++) {
					prob1[i][j] = 0;
				}
			}
		}

		int k = 1;
		double error;
		System.out.println("Start iterating: ");
		do {
			error = pagerank(vec, prob1);
			System.out.printf("%2d [%.3f]: ", k++, error);
			printVec(vec);
		} while (error > 0.001);
      
		Map<Integer, Double> mapVec = new HashMap<Integer, Double>();

		for (int i = 0; i < 10000; i++) {
			mapVec.put(i, vec[i]);
		}

		List<Map.Entry<Integer, Double>> infoIds = new ArrayList<Map.Entry<Integer, Double>>(
				mapVec.entrySet());

		// Sorting
		Collections.sort(infoIds, new Comparator<Map.Entry<Integer, Double>>() {
			public int compare(Map.Entry<Integer, Double> o1,
					Map.Entry<Integer, Double> o2) {
				// return (o2.getValue() - o1.getValue());
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});
		
		// Sorted
		for (int i = 0; i < 20; i++) {
			System.out.println(infoIds.get(i));
		}

		System.out.println("Finsh all");
	}

	static void queryScore() throws IOException, ParseException {
		Analyzer analyzer = new EnglishAnalyzer(Version.LUCENE_46); // simple
																	// tokenization
		Directory directory = new RAMDirectory(); // store the index in main
													// memory
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_46,
				analyzer);
		IndexWriter writer = new IndexWriter(directory, config);

		FieldType ft = new FieldType();
		ft.setTokenized(true);
		ft.setIndexed(true);
		ft.setStored(false);
		ft.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS);
		System.out.println("Start VSM index ");
		// index a document collection
		File[] files = new File("./Assignment2/contents").listFiles();
		for (File f : files) {
			String docID = f.getName();
			Document doc = new Document();
			FileReader r = new FileReader(f);
			doc.add(new Field("content", new FileReader(f), ft));
			doc.add(new StringField("docID", docID, Field.Store.YES));
			writer.addDocument(doc);
			// System.out.println("Indexing... " + docID);
		}

		// done with indexing
		writer.close();
		System.out.println("Finish VSM index ");

		// prepare for retrieving...
		IndexReader reader = DirectoryReader.open(directory);
		IndexSearcher searcher = new IndexSearcher(reader);

		// print out information in inverted index

		// document ranking
		QueryParser parser = new QueryParser(Version.LUCENE_46, "content",
				analyzer);
		Query query = parser
				.parse("the relationship between China and Hong Kong");
		TopDocs hits = searcher.search(query, reader.maxDoc()); 

		for (ScoreDoc scoreDoc : hits.scoreDocs) {
			Document doc = searcher.doc(scoreDoc.doc);
			int socreID = Integer.parseInt(doc.get("docID"));
			score[socreID] = scoreDoc.score;
		}

		// done!
		reader.close();
		directory.close();
	}

	static double pagerank(double[] vec, double[][] prob) {
		double[] newVec = new double[vec.length];

		for (int i = 0; i < vec.length; i++) {
			newVec[i] = 0;
			for (int j = 0; j < vec.length; j++) {
				newVec[i] += vec[j] * prob[j][i];
			}
		}

		double norm = 0;
		double error = 0;

		for (int i = 0; i < vec.length; i++)
			norm += newVec[i];

		for (int i = 0; i < vec.length; i++) {
			error += Math.abs(vec[i] - newVec[i] / norm);
			vec[i] = newVec[i] / norm;
		}

		return error;
	}

	static void printVec(double[] vec) {
		for (int i = 0; i < vec.length; i++) {
			System.out.printf("%.2f ", vec[i]);
		}
		System.out.println();
	}

	public static void indexURL() throws IOException {
		FileReader fr = new FileReader("./Assignment2/id2url");

		BufferedReader br = new BufferedReader(fr);
		String line = "";
		while ((line = br.readLine()) != null) {
			String[] newline = line.split(" ");
			int docID = Integer.parseInt(newline[0]);
			String URL = newline[1];
			urlMap.put(URL, docID);
            
		}
		System.out.println("indexURL finshed");
		br.close();

	}

	public static void constructMatrix() throws IOException {
		File[] files = new File("./Assignment2/anchor").listFiles();
		for (File f : files) {
			String[] newStr = f.getName().split("_");
			int docID = Integer.parseInt(newStr[0]);
			FileReader fr = new FileReader(f);
			BufferedReader br = new BufferedReader(fr);
			String line = "";
			while ((line = br.readLine()) != null) {
				String[] newline = line.split("\t");
				String URL = newline[1];
				if (urlMap.containsKey(URL)) {
					prob[docID][urlMap.get(URL)] = 1;
				}
				else if(URL.startsWith("#")){
					prob[docID][docID] = 1;
				}
			}
			br.close();
		}

		System.out.println("Finshed readFile");
	}

}
