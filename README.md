# kwoory

## What is it ?
kwoory is a command line tool, written in Groovy, designed to perform simple queries on database. The main idea is too make simple queries easier to type and make them a little less verbose.

For now, only MySQL is supported.

## How to build ?

With Gradle installed :

    gradle makeJar

## How to run ?

The best way is to create .sh / .bat file and add it in your path or in ```/usr/local/bin```

    #!/bin/bash
    $JAVA_HOME/bin/java -cp "/path/to/kwoory-{version}.jar:/path/containing/kwoory.config" KwooryMain $@

## How to use ?

    kwoory {from|group} TABLE/ALIAS [parameters] [with [columns]]
    
### Without any mapping

kwoory works with a configuration file but it's optional. If your table is already not too verbose, you can use it as it is. But you can't use any `WHERE` clause.

Let's take this simple table

```sql
mysql> desc foo;
+-------+-------------+------+-----+-------------------+-----------------------------+
| Field | Type        | Null | Key | Default           | Extra                       |
+-------+-------------+------+-----+-------------------+-----------------------------+
| id    | bigint(20)  | NO   | PRI | NULL              | auto_increment              |
| bar   | varchar(50) | NO   |     | NULL              |                             |
| baz   | timestamp   | NO   |     | CURRENT_TIMESTAMP | on update CURRENT_TIMESTAMP |
+-------+-------------+------+-----+-------------------+-----------------------------+
``` 

A simple `SELECT *` :
```
$ kwoory from foo
+----+-------------+-----------------------+
| id | bar         | baz                   |
+----+-------------+-----------------------+
| 1  | a bar       | 2016-11-02 16:22:35.0 |
| 2  | another bar | 2016-11-02 16:22:43.0 |
| 3  | a last bar  | 2016-11-02 16:22:51.0 |
+----+-------------+-----------------------+
3 rows in set
```

With a column selection :
```
$ kwoory from foo with bar
+-------------+
| bar         |
+-------------+
| a bar       |
| another bar |
| a last bar  |
+-------------+
3 rows in set
```

### With a mapping file

If you want to perform advance queries, you can add a mapping through a *.config* file

Let's take this verbose table

```sql
mysql> desc very_verbose_foo;
+------------------+-------------+------+-----+-------------------+-----------------------------+
| Field            | Type        | Null | Key | Default           | Extra                       |
+------------------+-------------+------+-----+-------------------+-----------------------------+
| very_verbose_id  | bigint(20)  | NO   | PRI | NULL              | auto_increment              |
| very_verbose_bar | varchar(50) | NO   |     | NULL              |                             |
| very_verbose_baz | timestamp   | NO   |     | CURRENT_TIMESTAMP | on update CURRENT_TIMESTAMP |
+------------------+-------------+------+-----+-------------------+-----------------------------+
``` 

Let's use the mapping :

```
display {
        style = 'auto'
        characterMaxSize = 200
}

credentials {
        type = 'mysql'
        host = 'your_host'
        schema = 'your_schema'
        user = 'your_user'
        password = 'your_password'
        properties {
                zeroDateTimeBehavior = 'convertToNull'
        }
}

aliases {
        tables {
                vv_foo = 'very_verbose_foo'
        }

        columns {
                vv_id = 'very_verbose_id'
                vv_bar = 'very_verbose_bar'
                vv_baz = 'very_verbose_baz'
        }
}

parameters {
        vv_foo {
                mandatory = []
                optional = ['very_verbose_id']
        }
}
```

 * credentials have been set
 * aliases have been set for the table name and its columns
 * a parameters list (mandatory and optional) has been defined in order to use a `WHERE` clause

A simple `SELECT *` :
```
$ kwoory from vv_foo
+-----------------+--------------------------+-----------------------+
| very_verbose_id | very_verbose_bar         | very_verbose_baz      |
+-----------------+--------------------------+-----------------------+
| 1               | a very verbose bar       | 2016-11-02 16:23:31.0 |
| 2               | another very verbose bar | 2016-11-02 16:23:37.0 |
| 3               | a last very verbose bar  | 2016-11-02 16:23:45.0 |
| 4               | a very verbose bar       | 2016-11-02 17:10:20.0 |
+-----------------+--------------------------+-----------------------+
4 rows in set
```

With a column selection :
```
$ kwoory from vv_foo with vv_bar
+--------------------------+
| very_verbose_bar         |
+--------------------------+
| a very verbose bar       |
| another very verbose bar |
| a last very verbose bar  |
| a very verbose bar       |
+--------------------------+
4 rows in set
```

With a column selection and a `WHERE` clause :
```
$ kwoory from vv_foo 1 with vv_bar
+--------------------+
| very_verbose_bar   |
+--------------------+
| a very verbose bar |
+--------------------+
1 row in set
```

**Parameters are determined according to their order. First mandatory ones then optional ones. You have to respect the parameter order defined in the configuration.** 

With a column selection and a `GROUP BY` clause :
```
$ kwoory group vv_foo with vv_bar
+--------------------------+-------+
| very_verbose_bar         | count |
+--------------------------+-------+
| a last very verbose bar  | 1     |
| a very verbose bar       | 2     |
| another very verbose bar | 1     |
+--------------------------+-------+
3 rows in set
```

### Display settings

The `display` section allows you to adjust the result display

 * *style = 'vertical|auto'* : vertical means results will always be presented vertically, like `\G` in MySQL, otherwise it will be determined dynamically.
 *  *characterMaxSize = integer* : in auto mode, the maximum number of characters after which the display will switch to vertical. Under this threshold (inclusive), the display will be horizontal
 *  *columnMaxSize = integer* : in auto mode, the maximum number of columns after which the display will switch to vertical. Under this threshold (inclusive), the display will be horizontal
 
 if `characterMaxSize` and `columnMaxSize` are both undefined, the style with be vertical
