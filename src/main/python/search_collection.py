#!/usr/bin/env python3

import argparse
import sys
import pymonetdb
import random
import string
from pymonetdb.control import Control
from topic_reader import TopicReader
from pprint import pprint

class SearchCollection:
   
    def getQueryTemplate(self):
        queryTemplate =  """
            WITH qterms AS (SELECT termid, docid, count FROM terms 
                WHERE termid IN ({})), 
                subscores AS (SELECT docs.collection_id, docs.id, len, term_tf.termid, 
                term_tf.tf, df, (log((528155-df+0.5)/(df+0.5))*((term_tf.tf*(1.2+1)/
                (term_tf.tf+1.2*(1-0.75+0.75*(len/188.33)))))) AS subscore 
                FROM (SELECT termid, docid, count as tf FROM qterms) AS term_tf 
                JOIN (SELECT docid FROM qterms 
                    GROUP BY docid HAVING COUNT(distinct termid) = {}) 
                    AS cdocs ON term_tf.docid = cdocs.docid 
                JOIN docs ON term_tf.docid = docs.id 
                JOIN dict ON term_tf.termid = dict.termid) 
            SELECT scores.collection_id, score FROM (SELECT collection_id, sum(subscore) AS score 
                FROM subscores GROUP BY collection_id) AS scores JOIN docs ON 
                scores.collection_id=docs.collection_id ORDER BY score DESC;
        """
        return queryTemplate

    def search(self):
        topics = self.topicReader.get_topics()
        ofile = open(self.args.output, 'w+')
        print("SCORING TOPICS")
        for topic in topics:
            query_terms = topic['title'].split(" ")
            ids = [] 
            for qterm in query_terms:
                self.cursor.execute(f"SELECT termid FROM dict WHERE dict.term = '{qterm}'")
                term_id = self.cursor.fetchone()
                if term_id:
                    ids.append(str(term_id[0]))
            term_ids = ", ".join(ids)
            sql_query = self.getQueryTemplate().format(term_ids, len(ids))
            self.cursor.execute(sql_query)
            output = self.cursor.fetchall()
            for rank, row in enumerate(output):
                collection_id, score = row
                ofile.write(f"{topic['number']} Q0 {collection_id} {rank + 1} {score} olddog\n")

    def getConnectionCursor(self):
        control = Control()
        
        print("CREATE DATABASE") 
        dbname = ''.join(random.choices(string.ascii_uppercase + string.digits, k=5))
        control.create(dbname)
        control.release(dbname) 
        
        print("CREATE CONNECTION")
        connection = pymonetdb.connect(username='monetdb',
                                       password='monetdb',
                                       hostname='localhost', 
                                       database=dbname)
        cursor = connection.cursor()
        
        print("CREATE TABLES")
        cursor.execute("CREATE TABLE docs (collection_id STRING, id INT, len INT)") 
        cursor.execute("CREATE TABLE dict (termid INT, term STRING, df INT)")
        cursor.execute("CREATE TABLE terms (termid INT, docid INT, count INT)")
         
        print("LOAD DATA")
        cursor.execute(f"COPY INTO docs FROM '{self.args.docs}'") 
        cursor.execute(f"COPY INTO dict FROM '{self.args.dict}'")
        cursor.execute(f"COPY INTO terms FROM '{self.args.terms}'")
        
        print("DATA LOADED")  
        return cursor 

    def __init__(self):
        parser = argparse.ArgumentParser()
        parser.add_argument('--terms', required=True, help='Path of terms file')
        parser.add_argument('--dict', required=True, help='Path of dict file')
        parser.add_argument('--docs', required=True, help='Path of docs file')
        parser.add_argument('--filename', required=True, help='Topics file')
        parser.add_argument('--output', required=True, help='filename for output')
        self.args = parser.parse_args()
        self.cursor = self.getConnectionCursor()
        self.topicReader = TopicReader(self.args.filename)  
        self.search() 
        
if __name__ == '__main__':
    SearchCollection()      
 
