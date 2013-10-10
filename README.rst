.. image:: ../../../elasticsearch-knapsack/raw/master/knapsack.png

by `DaPino <http://www.iconarchive.com/show/fishing-equipment-icons-by-dapino/backpack-icon.html>`_ `CC Attribution-Noncommercial 3.0 <http://creativecommons.org/licenses/by-nc/3.0/>`_

Elasticsearch Knapsack Plugin
=============================

Knapsack is an index export/import plugin for `Elasticsearch <http://github.com/elasticsearch/elasticsearch>`_.

It uses tar archive format and gzip compression for input/output.

Installation
------------

Current version of the plugin is **2.1.1** (Oct 10, 2013)

Prerequisites::

  Elasticsearch 0.90.5

Bintray:

https://bintray.com/pkg/show/general/jprante/elasticsearch-plugins/elasticsearch-knapsack

`Direct download <http://dl.bintray.com/jprante/elasticsearch-plugins/org/xbib/elasticsearch/plugin/elasticsearch-knapsack/2.1.1/elasticsearch-knapsack-2.1.1.zip>`_

Command::

  ./bin/plugin -url http://bit.ly/181K8BD -install knapsack


Do not forget to restart the node.

Documentation
-------------

Note: you must have the _source field enabled, otherwise the Knapsack export will not work.

Let's assume a simple index::

   curl -XDELETE localhost:9200/test
   curl -XPUT localhost:9200/test/test/1 -d '{"key":"value 1"}'
   curl -XPUT localhost:9200/test/test/2 -d '{"key":"value 2"}'

Exporting
---------

You can export this Elasticsearch index with::

   curl -XPOST localhost:9200/test/test/_export

The result is a file in the Elasticsearch folder::

   -rw-r--r--   1 joerg  staff    296  9 Dez 14:56 test_test.tar.gz
   
Check with tar utility, the settings and the mapping is also exported::   

   tar ztvf test_test.tar.gz 
   -rw-r--r--  0 0      0         116  9 Dez 14:56 test/_settings
   -rw-r--r--  0 0      0          49  9 Dez 14:56 test/test/_mapping
   -rw-r--r--  0 0      0          17  9 Dez 14:56 test/test/1
   -rw-r--r--  0 0      0          17  9 Dez 14:56 test/test/2

Also, you can export with::

   curl -XPOST localhost:9200/test/_export

with the result file test.tar.gz, or even all data with::

   curl -XPOST localhost:9200/_export

with the result file _all.tar.gz

Importing
---------

You can import the file with::

   curl -XPOST localhost:9200/test/test/_import

Be sure that the index does not exist. You must delete an index by hand. Knapsack does not delete or overwrite data.

You can import the file to a new index with renaming your file to test2_test2.tar.gz and executing the import command::

   mv test_test.tar.gz test2_test2.tar.gz
   curl -XPOST localhost:9200/test2/test2/_import

and check you have copied the data to a new index with::

   curl -XGET localhost:9200/test2/test2/1
   {"_index":"test2","_type":"test2","_id":"1","_version":1,"exists":true, "_source" : {"key":"value 1"}}

Choosing a different location
-----------------------------

With the ``target`` parameter, you can choose a path and alternative name for your tar archive. Example::

   curl -XPOST 'localhost:9200/_export?target=/big/space/archive.tar.gz'

Compression
-----------

You can select a ``.tar.gz``, ``.tar.bz2``, or ``.tar.xz`` suffix for the corresponding compression algorithm. Example::

   curl -XPOST 'localhost:9200/_export?target=/my/archive.tar.bz2'

Modifying settings and mappings
-------------------------------

You can overwrite the settings and mapping when importing by using parameters in the form ``<index>_settings=<filename>`` or ``<index>_<type>_mapping=<filename>``. 

General example::

    curl -XPOST 'localhost:9200/myindex/mytype/_import?myindex_settings=/my/new/mysettings.json&myindex_mytype_mapping=/my/new/mapping.json'

The following statements demonstrate how you can change the number of shards from the default ``5`` to ``1`` and replica from ``1`` to ``0`` for an index ``test``::

    curl -XDELETE localhost:9200/test
    curl -XPUT 'localhost:9200/test/test/1' -d '{"key":"value 1"}'
    curl -XPUT 'localhost:9200/test/test/2' -d '{"key":"value 2"}'
    curl -XPUT 'localhost:9200/test2/foo/1' -d '{"key":"value 1"}'
    curl -XPUT 'localhost:9200/test2/bar/1' -d '{"key":"value 1"}'
    curl -XPOST 'localhost:9200/test/_export'
    tar zxvf test.tar.gz test/_settings
    echo '{"index.number_of_shards":"1","index.number_of_replicas":"0","index.version.created":"200199"}' > test/_settings
    curl -XDELETE 'localhost:9200/test'
    curl -XPOST 'localhost:9200/test/_import?test_settings=test/_settings'
    curl -XGET 'localhost:9200/test/_settings?pretty'
    curl -XPOST 'localhost:9200/test/_search?q=*&pretty'

The result is::

  {
    "took" : 2,
    "timed_out" : false,
    "_shards" : {
      "total" : 1,
      "successful" : 1,
      "failed" : 0
    },
    "hits" : {
      "total" : 2,
      "max_score" : 1.0,
      "hits" : [ {
        "_index" : "test",
        "_type" : "test",
         "_id" : "1",
        "_score" : 1.0, "_source" : {"key":"value 1"}
      }, {
        "_index" : "test",
        "_type" : "test",
        "_id" : "2",
        "_score" : 1.0, "_source" : {"key":"value 2"}
      } ]
    }
  }


Caution
=======

Knapsack is very simple and works without locking or index snapshots.
So it is up to you to organize the safe export and import.
If the index changes while Knapsack is exporting, you may lose data in the export.
Do not run Knapsack in parallel on the same export.


License
=======

Elasticsearch Knapsack Plugin

Copyright (C) 2012 Jörg Prante

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
