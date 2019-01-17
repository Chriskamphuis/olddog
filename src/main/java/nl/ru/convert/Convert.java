package nl.ru.convert;

import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.ParserProperties;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class Convert {

    private HashMap<Integer, Pair<String, Integer>> dict;

    private Convert(Args args) throws IOException {

        Path indexPath = Paths.get(args.index);
        if (!Files.exists(indexPath) || !Files.isDirectory(indexPath) || !Files.isReadable(indexPath)) {
            throw new IllegalArgumentException(args.index + " does not exist or is not a directory.");
        }

        FileOutputStream docsDocIdWriter = new FileOutputStream(args.docs + "_docID");
        FileOutputStream lenWriter = new FileOutputStream(args.docs + "_len");

        FileOutputStream termIdWriter = new FileOutputStream(args.terms + "_termID");
        FileOutputStream termsDocIDWriter = new FileOutputStream(args.terms + "_docID");
        FileOutputStream countWriter = new FileOutputStream(args.terms+ "_count");

        this.dict = new HashMap<>();

        IndexReader reader;
        if (args.inmem) {
            reader = DirectoryReader.open(MMapDirectory.open(indexPath));
        } else {
            reader = DirectoryReader.open(FSDirectory.open(indexPath));
        }

        int termIdTemp = 0;
        HashMap<String, Integer> termIdMap = new HashMap<>();

        for (int i = 0; i < reader.maxDoc(); i++) {
            Terms termsVector = reader.getTermVector(i, "contents");
            reader.document(i);
            TermsEnum termsEnum = termsVector.iterator();
            int j = 0;
            while (termsEnum.next() != null) {
                PostingsEnum postings = termsEnum.postings(null, PostingsEnum.ALL);
                String termString = termsEnum.term().utf8ToString();
                int termId;
                if (termIdMap.get(termString) == null) {
                    termId = termIdTemp;
                    termIdTemp++;
                } else {
                    termId = termIdMap.get(termString);
                }
                termIdMap.put(termString, termId);
                if (this.dict.get(termId) == null) {
                    this.dict.put(termId, new Pair<>(termString, 1));
                } else {
                    int value = this.dict.get(termId).getSecond();
                    this.dict.put(termId, new Pair<>(termString, value + 1));
                }
                while(postings.nextDoc() != PostingsEnum.NO_MORE_DOCS){
                    termIdWriter.write(termId);
                    termsDocIDWriter.write(i);
                    countWriter.write(postings.freq());
                }
                j++;
            }
            docsDocIdWriter.write(i);
            lenWriter.write(j);
        }

        docsDocIdWriter.flush();
        lenWriter.flush();
        termIdWriter.flush();
        termsDocIDWriter.flush();
        countWriter.flush();

        docsDocIdWriter.close();
        lenWriter.close();
        termIdWriter.close();
        termsDocIDWriter.close();
        countWriter.close();
    }

    private void writeDictToFile(String filename) throws IOException {
        FileOutputStream termIdWriter = new FileOutputStream(filename + "_termID");
        BufferedWriter termWriter = new BufferedWriter(new FileWriter(filename + "_term"));
        FileOutputStream dfWriter = new FileOutputStream(filename+ "_df");

        for (Integer key : this.dict.keySet()) {
            Pair value = this.dict.get(key);
            termIdWriter.write(key);
            termWriter.write(value.getFirst().toString());
            termWriter.newLine();
            dfWriter.write((int) value.getSecond());
        }

        termIdWriter.flush();
        termWriter.flush();
        dfWriter.flush();

        termIdWriter.close();
        termWriter.close();
        dfWriter.close();
    }

    public static void main(String[] args) throws IOException {
        Args convertArgs = new Args();
        CmdLineParser parser = new CmdLineParser(convertArgs, ParserProperties.defaults().withUsageWidth(90));
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
            System.err.println("Example: "+ Convert.class.getSimpleName() +
                    parser.printExample(OptionHandlerFilter.REQUIRED));
            return;
        }
        Convert convert = new Convert(convertArgs);
        convert.writeDictToFile(convertArgs.dict);
    }
}