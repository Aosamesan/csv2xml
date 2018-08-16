import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CSV2XMLConverter {
    private CSVItem mainCSVItem;
    private List<CSVItem> subCSVItemList;

    public CSV2XMLConverter(String mainCSVPath, String... subCSVItemPaths) {
        mainCSVItem = new CSVItem(mainCSVPath);
        subCSVItemList = Stream.of(subCSVItemPaths).map(CSVItem::new).collect(Collectors.toList());
    }

    public Document convert() throws IOException, ParserConfigurationException {
        try (InputStream is = new FileInputStream(mainCSVItem.getPath());
             InputStreamReader reader = new InputStreamReader(is);
             BufferedReader bufferedReader = new BufferedReader(reader);
             LineSupplier mainLineSupplier = new LineSupplier(bufferedReader)) {
            String fragmentName = mainCSVItem.getName();
            XMLBuilder xmlBuilder = new XMLBuilder(fragmentName);
            List<String> mainHeaderList = mainLineSupplier.get();

            Map<String, Map<String, List<Element>>>  subItemListMap = createMultiItemMap(xmlBuilder);

            Stream.generate(mainLineSupplier)
                    .takeWhile(Objects::nonNull)
                    .map(list -> xmlBuilder.createMainElement(fragmentName, list, mainHeaderList, subItemListMap))
                    .forEach(xmlBuilder::appendToRootElement);

            return xmlBuilder.getDocument();
        }
    }

    private Map<String, Map<String, List<Element>>> createMultiItemMap(XMLBuilder xmlBuilder) {
        return subCSVItemList
                .stream()
                .collect(Collectors.toMap(CSVItem::getName, this.createMultiItemMapFunction(xmlBuilder)));
    }

    private Function<CSVItem, Map<String, List<Element>>> createMultiItemMapFunction(XMLBuilder xmlBuilder) {
        return csvItem -> createMultiItemMap(xmlBuilder, csvItem);
    }

    private Map<String, List<Element>> createMultiItemMap(Stream<Element> elementStream) {
        return elementStream.collect(Collectors.groupingBy(e -> e.getAttribute("ref")));
    }

    private Map<String, List<Element>> createMultiItemMap(XMLBuilder xmlBuilder, CSVItem subCSVItem) {
        try (InputStream is = new FileInputStream(subCSVItem.getPath());
             InputStreamReader reader = new InputStreamReader(is);
             BufferedReader bufferedReader = new BufferedReader(reader);
             LineSupplier lineSupplier = new LineSupplier(bufferedReader)) {
            List<String> headerList = lineSupplier.get();
            return createMultiItemMap(Stream.generate(lineSupplier)
                    .takeWhile(Objects::nonNull)
                    .map(list -> xmlBuilder.createSubMultiElement(subCSVItem.getName(), list, headerList)));
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    public static class LineSupplier implements Supplier<List<String>>, AutoCloseable {
        private Scanner scanner;

        public LineSupplier(Reader reader) {
            scanner = new Scanner(reader);
        }

        @Override
        public List<String> get() {
            return scanner.hasNext() ? List.of(scanner.nextLine().split("[,\t]")) : null;
        }

        @Override
        public void close() {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    public static class CSVItem {
        private static final Pattern EXTENSION_PATTERN = Pattern.compile("\\..*?$");
        private String path;
        private String name;

        public CSVItem(String path) {
            this.path = path;
            String[] split = path.split("[/\\\\]");
            this.name = EXTENSION_PATTERN.matcher(split[split.length - 1]).replaceAll("");
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }
    }

    public static class XMLBuilder {
        private final DocumentBuilderFactory documentBuilderFactory;
        private final DocumentBuilder documentBuilder;
        private Document document;
        private Element rootElement;

        public XMLBuilder(String fragmentName) throws ParserConfigurationException {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            document = documentBuilder.newDocument();
            rootElement = document.createElement(fragmentName + "List");
            document.appendChild(rootElement);
        }

        public Element createTextElement(String name, String data) {
            Element result = document.createElement(name);
            result.setTextContent(data);
            return result;
        }

        public Element createMultiElement(String name, Iterable<? extends Node> nodes) {
            Element result = document.createElement(name);
            for (Node node : nodes) {
                result.appendChild(node);
            }
            return result;
        }

        public Element createMainElement(String fragmentName, List<String> dataList, List<String> headerList, Map<String, Map<String, List<Element>>> subItemListMap) {
            String id = dataList.get(0);
            int columnSize = headerList.size();
            Element mainElement = document.createElement(fragmentName);

            for (int i = 0; i < columnSize; i++) {
                String header = headerList.get(i);
                Element subElement;
                if (subItemListMap.containsKey(header)) {
                    String listName = header + "List";
                    List<Element> subItemList = subItemListMap.get(header).get(id).stream().map(removeAttribute("ref")).collect(Collectors.toList());
                    subElement = createMultiElement(listName, subItemList);
                } else {
                    String item = "";
                    if (dataList.size() > i) {
                        item = dataList.get(i);
                    }
                    subElement = createTextElement(header, item);
                }
                mainElement.appendChild(subElement);
            }

            return mainElement;
        }

        public Element createSubMultiElement(String fragmentName, List<String> dataList, List<String> headerList) {
            String id = dataList.get(0);
            Element element = document.createElement(fragmentName);
            element.setAttribute("ref", id);
            int columnSize = headerList.size();
            for (int i = 1; i < columnSize; i++) {
                String header = headerList.get(i);
                String data = dataList.get(i);
                Element sub = document.createElement(header);
                sub.setTextContent(data);
                element.appendChild(sub);
            }
            return element;
        }

        public void appendToRootElement(Element element) {
            rootElement.appendChild(element);
        }

        public Document getDocument() {
            return document;
        }

        private Function<Element, Element> removeAttribute(String attributeName) {
            return e -> {
                e.removeAttribute(attributeName);
                return e;
            };
        }
    }
}
