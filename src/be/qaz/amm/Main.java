package be.qaz.amm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import be.qaz.amm.generator.BeanGenerator;
import be.qaz.amm.generator.JsonGenerator;
import be.qaz.amm.generator.ParserGenerator;
import be.qaz.amm.model.Field;
import be.qaz.amm.model.Table;

public class Main {

	final static String inputDirectoryPath = "etc/input";
	final static String outputDirectoryPath = "etc/export/";

	public static ArrayList<Field> currentTableFields;

	public static void main(String[] args) {
		System.out.println("START");
		Constants.primaryKeys = new ArrayList<Field>();
		Constants.tables = new ArrayList<Table>();
		Constants.junctionTables = new ArrayList<Table>();

		Constants.tables = new ArrayList<Table>();
		currentTableFields = new ArrayList<Field>();
		File file = new File(inputDirectoryPath);
		for (File f : loadFiles(file)) {
			System.out.println("######## " + f.getName() + " FOUND ########");
			System.out.println("NEW PARSING");
			fileParser(f);
		}

		generateParsers(Constants.tables);
		// generateJavaBeans(Constants.tables);
		generateJsonScheme(Constants.tables);

		System.out.println("######## END ######## ");
		System.out.println("######## TABLES DETAILS ########");
		System.out.println("TOTAL Constants.tables = " + Constants.tables.size());
		for (Table t : Constants.tables) {
			System.out.println(t.toString());
		}
	}

	public static void generateParsers(ArrayList<Table> tables) {
		System.out.println("######## GENERATED PARSER ########");
		ArrayList<String> generated = new ArrayList<String>();
		for (Table t : tables) {
			if (t != null && t.getFields() != null && t.getFields().size() > 0) {
				generated.addAll(ParserGenerator.generateJavaParser(t));
			}
		}

		if (generated.size() > 0) {
			String[] data = generated.toArray(new String[generated.size()]);
			writeFile("JsonParsers.java", data);
			generated.clear();
		}
	}

	public static void generateJsonScheme(ArrayList<Table> tables) {
		System.out.println("######## GENERATED JSON SCHEME ########");
		ArrayList<String> generated = new ArrayList<String>();
		for (Table t : tables) {
			if (t != null) {
				generated = JsonGenerator.generateJsonSchema(t);
				if (generated != null) {
					if (generated.size() > 0) {
						String[] data = generated.toArray(new String[generated.size()]);
						writeFile(t.getOriginalName() + ".json", data);
						generated.clear();
					}
				}
			}
		}
	}

	public static void generateJavaBeans(ArrayList<Table> tables) {
		System.out.println("######## GENERATED JAVA BEANS ########");
		ArrayList<String> generated = new ArrayList<String>();
		for (Table t : tables) {
			if (t != null) {
				generated = BeanGenerator.generateJavaBean(t);
				if (generated != null) {
					for (String string : generated) {
						System.out.println(string);
					}
				}
				if (generated != null && generated.size() > 0) {
					String[] data = generated.toArray(new String[generated.size()]);
					writeFile(t.getName() + ".class", data);
					generated.clear();
				}
			}
		}
	}

	public static void writeFile(String name, String[] lines) {
		BufferedWriter writer = null;
		try {
			new File(outputDirectoryPath).mkdirs();
			final File outFile = new File(outputDirectoryPath + name);

			System.out.println(outFile.getCanonicalPath());
			writer = new BufferedWriter(new FileWriter(outFile));

			String line;
			for (final String line2 : lines) {
				line = String.format("%s \n", line2);
				writer.write(line);
			}

		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			try {
				writer.flush();
				writer.close();
			} catch (final Exception e) {
			}
		}
	}

	// Load files into prog
	public static File[] loadFiles(File directoryPath) {
		final File[] entityFiles = directoryPath.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return !pathname.getName().startsWith("_") && pathname.getName().endsWith(".json");
			}
		});
		return entityFiles;
	}

	public static void fileParser(File file) {
		if (file == null) {
			return;
		}
		final JSONParser parser = new JSONParser();
		try {
			final Object obj = parser.parse(new FileReader(file));
			JSONObject jsonObject = null;
			boolean isInArray = false;
			if (obj instanceof JSONArray) {
				final JSONArray jsonArray = (JSONArray) obj;
				jsonObject = (JSONObject) jsonArray.get(0);
				isInArray = true;
			} else if (obj instanceof JSONObject) {
				jsonObject = (JSONObject) obj;
			} else {
				return;
			}

			final String rootObject = file.getName().replace(".json", "");
			// parseJsonObject(jsonObject, rootObject, null, isInArray);
			parseJsonObject(jsonObject, rootObject, null, true);

		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Parse your JSONObject and fill the Constants.tables array.
	 * 
	 * @param obj
	 *            JSONObject : you're about to parse
	 * @param currentObjName
	 *            String : the name of obj
	 * @param exObjName
	 *            String : the name of the object containing obj, ex : "recipes"
	 *            contains "variants"
	 */
	public static void parseJsonObject(JSONObject obj, String currentObjName, String exObjName, boolean isInArray) {

		Table currentTable;
		String key;
		Object aObj;
		boolean exObjAlreadySet = false;

		if (currentObjName != null && !currentObjName.isEmpty() && !Utils.tableAlreadyExists(currentObjName)) {
			currentTable = new Table(currentObjName, Utils.getNameCamelCase(currentObjName), null, null, isInArray);
			Constants.tables.add(currentTable);
		}

		Iterator iter = obj.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			key = (String) entry.getKey();
			aObj = obj.get(key);

			if (exObjName != null && !exObjAlreadySet) {
				createField(exObjName + "FkId", currentObjName, Constants.JUNCTION, exObjName, aObj);
				exObjAlreadySet = true;
			}

			if (aObj instanceof JSONObject) {
				System.out.println("IN " + currentObjName + " # OBJ : " + key);
				parseJsonObject((JSONObject) aObj, key, currentObjName, false);

			} else if (aObj instanceof JSONArray) {

				JSONArray array = (JSONArray) aObj;
				System.out.println("IN " + currentObjName + " # ARRAY : " + key);
				if (array.size() > 0) {
					Object bObj = array.get(0);

					if (bObj instanceof JSONObject) {
						parseJsonObject((JSONObject) bObj, key, currentObjName, true);
					} else {
						System.out.println("++++ parseJsonObject/ARRAY key = " + key + " aObj = " + aObj
								+ " currentObjName " + currentObjName);
						createField(key, currentObjName, Constants.ARRAY, null, bObj);
					}
				}

			} else {
				final String type = Utils.javaTypeResolver(aObj);
				createField(key, currentObjName, type, null, aObj);
			}
		}
	}

	/**
	 * createField
	 * 
	 * @param fieldName
	 *            String : field name
	 * @param value
	 *            Object : content value used to determined its type
	 * @param table
	 *            String : field's table
	 * @return created field
	 */
	public static Field createField(String fieldName, String table, String type, String constraint, Object value) {
		Field field = null;
		Table t = Utils.findTableWithName(constraint);

		if ((fieldName == null) || (table == null)) {
			return null;
		}
		String javaFieldName = Utils.getNamePascalCase(fieldName);

		// if (type == null) {
		// type = Constants.INT;
		// }

		if (type.equalsIgnoreCase(Constants.JUNCTION) && Utils.isTagAllowed(fieldName)) {
			if (constraint == null) {
				constraint = Utils.extractTableFromUri((String) value);
			}
			final Table junc = createJunctionTable(table, constraint);
			if (junc != null) {
				Constants.tables.add(junc);
				Constants.junctionTables.add(junc);
			}

			if (t != null) {
				if (t.getFields() != null) {
					field = new Field(table + "FkId", table + "FkId", Constants.CALLER, table);
					if (!Utils.fieldAlreadyExistsInTable(field, t)) {
						System.out.println("CALLER added + " + table + "FkId");
						t.getFields().add(field);
					}
				} else {
					field = new Field(table + "FkId", table + "FkId", Constants.CALLER, table);
					if (!Utils.fieldAlreadyExistsInTable(field, t)) {
						final ArrayList<Field> af = new ArrayList<Field>();
						af.add(field);
						t.setFields(af);
					}
				}
			}
		} else if (type.equalsIgnoreCase(Constants.ARRAY)) {
			final String arrayType = Utils.javaTypeResolver(value);
			if (arrayType.equalsIgnoreCase(Constants.URI)) {
				if (constraint == null) {
					constraint = Utils.extractTableFromUri((String) value);
				}
				final Table junc = createJunctionTable(table, constraint);
				if (junc != null) {
					Constants.tables.add(junc);
					Constants.junctionTables.add(junc);
				}
			} else {
				final Table arrayTb = createArrayTable(table, fieldName, arrayType);
				if (arrayTb != null) {
					Constants.tables.add(arrayTb);
				}
			}
		} else if (type.equalsIgnoreCase(Constants.URI)) {
			// javaFieldName += "Fk";
			constraint = Utils.extractTableFromUri((String) value);
		}

		field = new Field(fieldName, javaFieldName, type, constraint);

		t = Utils.findTableWithName(table);
		if (t != null) {
			ArrayList<Field> fs = t.getFields();
			if (fs == null) {
				fs = new ArrayList<Field>();
			}
			if (!Utils.fieldAlreadyExistsInTable(field, t)) {
				fs.add(field);
				t.setFields(fs);
			}
		}
		return field;
	}

	/**
	 * Create table containing array of primitive value.
	 * 
	 * @param foreign
	 *            String : linked table name
	 * @param table
	 *            String : table name
	 * @param type
	 *            String : type of the array
	 * @return created table
	 */
	public static Table createArrayTable(String foreign, String table, String type) {
		if (!Utils.tableAlreadyExists(table)) {
			final ArrayList<Field> fs = new ArrayList<Field>();
			final Field f1 = new Field(foreign + "_id", Utils.getNamePascalCase(foreign) + "Id", Constants.INT, foreign);
			final Field f2 = new Field(table, Utils.getNamePascalCase(table), type, null);
			fs.add(f1);
			fs.add(f2);
			final Table arrayTable = new Table(table, Utils.getNameCamelCase(table), Constants.JUNCTION_TABLE, fs,
					false);
			return arrayTable;
		}
		return null;
	}

	public static Table createJunctionTable(String refTable, String extTable) {
		final String tName = Utils.createJunctionTableName(refTable, extTable);
		if (!Utils.tableAlreadyExists(tName)) {
			final ArrayList<Field> fs = new ArrayList<Field>();
			final Field f1 = new Field(refTable + "_id", Utils.getNamePascalCase(refTable) + "Id", Constants.INT, refTable);
			final Field f2 = new Field(extTable + "_id", Utils.getNamePascalCase(extTable) + "Id", Constants.INT, extTable);
			fs.add(f1);
			fs.add(f2);
			final Table juncTable = new Table(tName, Utils.getNameCamelCase(tName), Constants.JUNCTION_TABLE, fs, false);
			return juncTable;
		}
		return null;
	}
}
