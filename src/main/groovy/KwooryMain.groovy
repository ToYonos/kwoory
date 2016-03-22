import groovy.sql.Sql
import groovy.transform.Field

import java.sql.SQLSyntaxErrorException
import java.util.logging.Level
import java.util.logging.Logger


Logger.getLogger("").setLevel(Level.SEVERE)

@Field err = {
	System.err.println it
	System.exit(1)
}

@Field final CONFIG_FILE = "kwoory.config"
@Field final operations = ['from', 'group', 'help']
@Field final drivers = [mysql: 'com.mysql.jdbc.Driver']

@Field final WITH = 'with'
@Field final VERTICAL = 'vertical'

@Field config = {
	def configUrl = getClass().getClassLoader().getResource(CONFIG_FILE)
	if (!configUrl) err("$CONFIG_FILE could not be found in classpath")
	new ConfigSlurper().parse(configUrl)
}()
@Field sql = {
	if (!drivers.get(config.credentials.type)) err "Unsupported database type : '${config.credentials.type}'"
	def url = "jdbc:${config.credentials.type}://${config.credentials.host}/${config.credentials.schema}"
	if (config.credentials.properties)
	{
		url += "?"
		config.credentials.properties.each { url+= it.key + "=" + it.value }
	}
	return Sql.newInstance(url.toString(), config.credentials.user, config.credentials.password, drivers.get(config.credentials.type))
}()

@Field extractParameters = {
	def params = Arrays.asList(it).subList(2, it.length)
	return new ArrayList(params.indexOf(WITH) != -1 ? params.subList(0, params.indexOf(WITH)) : params)
}

@Field extractColumns = {
	def params = Arrays.asList(it).subList(2, it.length)
	return new ArrayList(params.indexOf(WITH) != -1 ? params.subList(params.indexOf(WITH) + 1, params.size()) : [])
}

@Field executeQuery = { query, parameters ->
	def metaData, rows
	try
	{
		rows = sql.rows(query, parameters) { metaData = it }
	}
	catch (SQLSyntaxErrorException e)
	{
		err e.getMessage()
	}

	if (rows.isEmpty())
	{
		println "Empty result"
	}
	else
	{
		def displayRow = {
			it instanceof byte[] ? new String(it) : it
		}
		
		def columnSize = [:]
		def totalSize = 1
		if (config.display.style != VERTICAL)
		{
			rows.each { row ->
				for (i in 1..metaData.getColumnCount())
				{
					def size = Math.max(columnSize.get(metaData.getColumnName(i)) ?: 0, Math.max(metaData.getColumnName(i).size(), displayRow(row.getAt(i-1)).toString().size()))
					columnSize.put(metaData.getColumnName(i), size)
				}
			}
			columnSize.each { totalSize += it.value + 3 }
		}

		def displayVertical = config.display.style == VERTICAL ||
			(config.display.characterMaxSize && totalSize > config.display.characterMaxSize) ||
			(config.display.columnMaxSize && config.display.columnMaxSize > metaData.getColumnCount())

		if (displayVertical)
		{
			def rowId = 1
			rows.each { row ->
				def maxSize = 0
				for (i in 1..metaData.getColumnCount())
				{
					if (metaData.getColumnName(i).size() > maxSize) maxSize = metaData.getColumnName(i).size()
				}
				println "*************************** ${rowId++}. row ***************************"
				for (i in 1..metaData.getColumnCount())
				{
					println String.format("%${maxSize}s: %s", metaData.getColumnName(i), displayRow(row.getAt(i-1)))
				}
			}
		}
		else
		{
			def separator = '+'
			def appendSeparator = { i ->
				i.times { separator += '-' }
				separator += '+'
			}

			for (i in 1..metaData.getColumnCount())
			{
				separator = appendSeparator columnSize.get(metaData.getColumnName(i))+2
			}

			println separator
			for (i in 1..metaData.getColumnCount())
			{
				print String.format("| %-${columnSize.get(metaData.getColumnName(i))}s ", metaData.getColumnName(i))
			}
			println '|'
			println separator
			
			rows.each { row ->
				for (i in 1..metaData.getColumnCount())
				{
					print String.format("| %-${columnSize.get(metaData.getColumnName(i))}s ", displayRow(row.getAt(i-1)))
				}
				println '|'
				println separator
			}
		}
		println "${rows.size()} ${rows.size() == 1 ? 'row' : 'rows'} in set\n"
	}
}

@Field appendParameter = { column, appendAnd ->
	def str = column + " = ?"
	if (appendAnd) str += " AND "
	return str
}

@Field handleFrom = {
	def alias = this.args[1]
	def table = config.aliases.tables.get(alias) ?: alias
	def fromParameters = extractParameters this.args
	
	def columns = extractColumns(this.args).collect { config.aliases.columns.get(it) ?: it }.join(', ') ?: "*"
	def query = "SELECT $columns FROM $table"
	if (!fromParameters.isEmpty()) query += " WHERE "

	def parametersIdx = fromParameters.size()
	if (((config.parameters.get(alias)?.mandatory?.size() ?: 0) + (config.parameters.get(alias)?.optional?.size() ?: 0)) < fromParameters.size())
	{
		if (!config.aliases.tables.get(alias)) err "The alias '$alias' does not exist"
		else err "Too many parameters, mandatory parameters : " + (config.parameters.get(alias)?.mandatory ?: 'none') + ", optional parameters : " + (config.parameters.get(alias)?.optional ?: 'none')
	}

	config.parameters."$alias".mandatory.each {
		if (!parametersIdx) err "Missing mandatory parameter for the alias '$alias', mandatory parameters : " + config.parameters."$alias".mandatory
		query += appendParameter it, --parametersIdx > 0
	}

	config.parameters."$alias".optional.each {
		if (!parametersIdx) return
		query += appendParameter it, --parametersIdx > 0
	}

	executeQuery query, fromParameters
}

@Field handleGroup = {
	def extractFromParameters = {
		def params = Arrays.asList(it).subList(2, it.length)
		return new ArrayList(params.indexOf(WITH) != -1 ? params.subList(0, params.indexOf(WITH)) : params)
	}

	def alias = this.args[1]
	def table = config.aliases.tables.get(alias) ?: alias
	def groupParameters = extractParameters this.args
	def columns = extractColumns(this.args).collect { config.aliases.columns.get(it) ?: it }.join(', ')

	if (!columns) err 'The group operation needs at least one column'

	def query = "SELECT $columns, count(*) as count FROM $table"
	if (!groupParameters.isEmpty()) query += " WHERE "

	def parametersIdx = groupParameters.size()
	(config.parameters."$alias".mandatory + config.parameters."$alias".optional).each {
		if (!parametersIdx) return
		query += appendParameter it, --parametersIdx > 0
	}

	query += " group by $columns"

	executeQuery query, groupParameters
}

@Field handleHelp = {
	println "# Available operations :"
	operations.each { println "- $it" }
	println ''
	println "# Available tables aliases :"
	if (config.aliases.tables)
	{
		config.aliases.tables.each {
			println "- ${it.key} -> ${it.value}"
			println " > Mandatory parameters : " + (config.parameters."${it.key}".mandatory ?: "none")
			println " > Optional parameters : " + (config.parameters."${it.key}".optional ?: "none")
			println ''
		}
	}
	else
	{
		println "- No table alias"
	}
	println "# Available columns aliases :"
	if (config.aliases.columns)
	{
		config.aliases.columns.each { println "- ${it.key} -> ${it.value}" }
	}
	else
	{
		println "- No column alias"
	}
}

if (this.args.length < 1) err "Missing operation, possible values : " + operations
if (this.args[0] != 'help' && this.args.length < 2) err "Missing table or alias, available aliases: " + config.aliases.tables.keySet()

def operation = this.args[0].substring(0, 1).toUpperCase() + this.args[0].toLowerCase().substring(1)
try
{
	"handle$operation"()
}
catch (MissingMethodException e)
{
	err "Unknown operation " + operation.toLowerCase() + "', possible values : " + operations
}
finally
{
	sql.close()
}