.. image:: ../../../elasticsearch-knapsack/raw/master/src/site/resources/knapsack.png

Image by `DaPino <http://www.iconarchive.com/show/fishing-equipment-icons-by-dapino/backpack-icon.html>`_ `CC Attribution-Noncommercial 3.0 <http://creativecommons.org/licenses/by-nc/3.0/>`_

Elasticsearch Knapsack Plugin
=============================

Knapsack is an export/import plugin for `Elasticsearch <http://github.com/elasticsearch/elasticsearch>`_.

It uses archive formats (tar, zip, cpio) and compression algorithms (gzip, bzip2, lzf, xz) for transfer.

A direct copy of indexes or index types, or any search results with stored fields is also supported.

Optionally, you can transfer archives to Amazon S3.

Installation
------------

.. image:: https://travis-ci.org/jprante/elasticsearch-knapsack.png

Prerequisites::

  Elasticsearch 0.90+

=============  =================  =================  ===========================================================
ES version     Plugin             Release date       Command
-------------  -----------------  -----------------  -----------------------------------------------------------
0.90.9         0.90.9.1           Jan 9, 2014        ./bin/plugin --install knapsack --url http://bit.ly/1e81hwh
0.90.9         0.90.9.1 (S3)      Jan 9, 2014        ./bin/plugin --install knapsack --url http://bit.ly/K8QwOJ
0.90.10        0.90.10.1          Jan 14, 2014       ./bin/plugin --install knapsack --url http://bit.ly/1j5rOy2
0.90.10        0.90.10.1 (S3)     Jan 14, 2014       ./bin/plugin --install knapsack --url http://bit.ly/1d3kYkp
1.0.0.RC1      1.0.0.RC1.1        Jan 16, 2014       ./bin/plugin --install knapsack --url http://bit.ly/19vmQHu
1.0.0.RC1      1.0.0.RC1.1 (S3)   Jan 16, 2014       ./bin/plugin --install knapsack --url http://bit.ly/1m82PHw
=============  =================  =================  ===========================================================

The S3 version includes Amazon AWS API support, it can optionally transfer archives to S3.

Do not forget to restart the node after installation.

Project docs
------------

The Maven project site is available at `Github <http://jprante.github.io/elasticsearch-knapsack>`_

Binaries
--------

Binaries (also older versions) are available at `Bintray <https://bintray.com/pkg/show/general/jprante/elasticsearch-plugins/elasticsearch-knapsack>`_

Overview
========

.. image:: ../../../elasticsearch-knapsack/raw/master/src/site/resources/knapsack-diagram.png


Example
=======

Let's assume a simple index::

   curl -XDELETE localhost:9200/test
   curl -XPUT localhost:9200/test/test/1 -d '{"key":"value 1"}'
   curl -XPUT localhost:9200/test/test/2 -d '{"key":"value 2"}'

Exporting to archive
--------------------

You can export this Elasticsearch index with::

   curl -XPOST localhost:9200/test/test/_export
   {"running":true,"mode":"export","archive":"tar","path":"file:test_test.tar.gz"}

The result is a file in the Elasticsearch folder::

   -rw-r--r--  1 es  staff  341  8 Jan 22:25 test_test.tar.gz
   
Check with tar utility, the settings and the mapping is also exported::

    tar ztvf test_test.tar.gz
    ----------  0 es     0         132  8 Jan 22:25 test/_settings/null/null
    ----------  0 es     0          49  8 Jan 22:25 test/test/_mapping/null
    ----------  0 es     0          17  8 Jan 22:25 test/test/2/_source
    ----------  0 es     0          17  8 Jan 22:25 test/test/1/_source

Also, you can export a whole index with::

   curl -XPOST localhost:9200/test/_export

with the result file test.tar.gz, or even all cluster indices with::

   curl -XPOST 'localhost:9200/_export'

to the file _all.tar.gz

By default, the archive format is `tar` with compression `gz` (gzip).

You can also export to `zip` or `cpio` archive or use another compression scheme.
Available are `bz2` (bzip2), `xz` (Xz), or `lzf` (LZF)

Export search results
----------------------

You can add a query to the `_export` endpoint just like you would do for searching in Elasticsearch::

   curl -XPOST 'localhost:9200/test/test/_export' -d '{
       "query" : {
           "match" : {
               "myfield" : "myvalue"
           }
       },
       "fields" : [ "_parent", "_source" ]
   }'

Export to an archive with a given path name
-------------------------------------------

You can configure an archive path with the parameter `path`

    curl -XPOST 'localhost:9200/test/_export?path=/tmp/myarchive.zip'

If ELasticsearch can not write an archive to the path, an error message will appear
and no export will take place.

Renaming indexes and index types
--------------------------------

You can rename indexes and index types by adding a `map` parameter that contains a JSON
object with old and new index (and index/type) names::

    curl -XPOST 'localhost:9200/test/type/_export?map=\{"test":"testcopy","test/type":"testcopy/typecopy"\}'

Copy to local or remote cluster
-------------------------------

If your requirement is not saving data to an archive at all, but only copying, Knapsack is your friend.

You can copy an index in the local cluster or to a remote cluster with the `_export/copy` endpoint.
Preconditions are: you have the same Java JVM version and the same Elasticsearch version.

Example for a local cluster copy of the index `test`::

    curl -XPOST 'localhost:9200/test/_export/copy?map=\{"test":"testcopy"\}'

Example for a remote cluster copy of the index ``test by using the parameters `cluster`, `host`, and `port`::

    curl -XPOST 'localhost:9200/test/_export/copy?&cluster=remote&host=127.0.0.1&port=9201'

This is a complete example that illustrates how to filter an index by timestamp and copy this part to
another index::

    curl -XDELETE 'localhost:9200/test'
    curl -XDELETE 'localhost:9200/testcopy'
    curl -XPUT 'localhost:9200/test/' -d '
    {
        "mappings" : {
            "_default_": {
                "_timestamp" : { "enabled" : true, "store" : true, "path" : "date" }
            }
        }
    }
    '
    curl -XPUT 'localhost:9200/test/doc/1' -d '
    {
        "date" : "2014-01-01T00:00:00",
        "sentence" : "Hi!",
        "value" : 1
    }
    '
    curl -XPUT 'localhost:9200/test/doc/2' -d '
    {
        "date" : "2014-01-02T00:00:00",
        "sentence" : "Hello World!",
        "value" : 2
    }
    '
    curl -XPUT 'localhost:9200/test/doc/3' -d '
    {
        "date" : "2014-01-03T00:00:00",
        "sentence" : "Welcome!",
        "value" : 3
    }
    '
    curl 'localhost:9200/test/_refresh'
    curl -XPOST 'localhost:9200/test/_export/copy?map=\{"test":"testcopy"\}' -d '
    {
        "fields" : [ "_timestamp", "_source" ],
        "query" : {
             "filtered" : {
                 "query" : {
                     "match_all" : {
                     }
                 },
                 "filter" : {
                    "range": {
                       "_timestamp" : {
                           "from" : "2014-01-02"
                       }
                    }
                 }
             }
         }
    }
    '
    curl '0:9200/test/_search?fields=_timestamp&pretty'
    # wait for bulk flush interval
    sleep 10
    curl '0:9200/testcopy/_search?fields=_timestamp&pretty'

Import
------

You can import the file with::

   curl -XPOST 'localhost:9200/test/test/_import'

Knapsack does not delete or overwrite data by default.
But ou can use the parameter `createIndex` with the value `false` to allow indexing to indexes that exist.

When importing, you can map your indexes or index/types to your favorite ones.

    curl -XPOST 'localhost:9200/test/_import?map=\{"test":"testcopy"\}'

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

Transferring archives to Amazon S3
----------------------------------

By using special plugin releases including the Amazon AWS S3 API, you can optionally transfer archives
to S3 or fetch one before importing. You can use the endpoints `_export/s3` and _import/s3` for that.

Export example::

    curl -XPOST 'localhost:9200/test/_export/s3?uri=s3://accesskey:secretkey@awshostname&bucketName=mybucket&key=mykey'

Import example::

    curl -XPOST 'localhost:9200/test/_import/s3?uri=s3://accesskey:secretkey@awshostname&bucketName=mybucket&key=mykey'

Note, the file name which is used for downloading from S3 is `mybucket/mykey` and the directory will be created
if it does not exist.


Check the state of running import/export
----------------------------------------

While exports or imports or running, you can check the state with::

    curl -XGET 'localhost:9200/_export/state'

or::

    curl -XGET localhost:9200/_import/state


Caution
=======

Knapsack is very simple and works without locks or snapshots. This means, if Elasticsearch is
allowed to write to the part of your data in the export while it runs, you may lose data in the export.
So it is up to you to organize the safe export and import with this plugin.

If you want a snapshot/restore feature, please use the standard napshot/restore in the upcoming
Elasticsearch 1.0 release.

Credits
=======

Knapsack contains derived work of Apache Common Compress
http://commons.apache.org/proper/commons-compress/

The code in this component has many origins:
The bzip2, tar and zip support came from Avalon's Excalibur, but originally
from Ant, as far as life in Apache goes. The tar package is originally Tim Endres'
public domain package. The bzip2 package is based on the work done by Keiron Liddle as
 well as Julian Seward's libbzip2. It has migrated via:
Ant -> Avalon-Excalibur -> Commons-IO -> Commons-Compress.
The cpio package has been contributed by Michael Kuss and the jRPM project.

Thanks to `nicktgr15 <https://github.com/nicktgr15>` for extending Knapsack to support Amazon S3.

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
