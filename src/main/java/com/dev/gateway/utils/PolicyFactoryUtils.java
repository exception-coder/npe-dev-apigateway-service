package com.dev.gateway.utils;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public class PolicyFactoryUtils {

    private static final PolicyFactory policy = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.IMAGES);

    public static void main(String[] args) {
        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>HTML Tags Example</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "    <h1>HTML Tags Example</h1>\n" +
                "    \n" +
                "    <!-- 文本标签 -->\n" +
                "    <h2>Text Elements</h2>\n" +
                "    <p>This is a <strong>paragraph</strong> with some <em>emphasized</em> and <strong>bold</strong> text.</p>\n" +
                "    <blockquote>This is a blockquote.</blockquote>\n" +
                "    <pre>This is preformatted text.</pre>\n" +
                "    <code>This is some inline code.</code>\n" +
                "    \n" +
                "    <!-- 标题 -->\n" +
                "    <h2>Headings</h2>\n" +
                "    <h1>Heading 1</h1>\n" +
                "    <h2>Heading 2</h2>\n" +
                "    <h3>Heading 3</h3>\n" +
                "    <h4>Heading 4</h4>\n" +
                "    <h5>Heading 5</h5>\n" +
                "    <h6>Heading 6</h6>\n" +
                "\n" +
                "    <!-- 列表 -->\n" +
                "    <h2>Lists</h2>\n" +
                "    <ul>\n" +
                "        <li>Unordered list item 1</li>\n" +
                "        <li>Unordered list item 2</li>\n" +
                "        <li>Unordered list item 3</li>\n" +
                "    </ul>\n" +
                "    <ol>\n" +
                "        <li>Ordered list item 1</li>\n" +
                "        <li>Ordered list item 2</li>\n" +
                "        <li>Ordered list item 3</li>\n" +
                "    </ol>\n" +
                "    <dl>\n" +
                "        <dt>Definition Term 1</dt>\n" +
                "        <dd>Definition Description 1</dd>\n" +
                "        <dt>Definition Term 2</dt>\n" +
                "        <dd>Definition Description 2</dd>\n" +
                "    </dl>\n" +
                "\n" +
                "    <!-- 链接与图像 -->\n" +
                "    <h2>Links and Images</h2>\n" +
                "    <a href=\"https://www.example.com\">This is a link</a>\n" +
                "    <img src=\"https://via.placeholder.com/150\" alt=\"Example Image\">\n" +
                "\n" +
                "    <!-- 表格 -->\n" +
                "    <h2>Tables</h2>\n" +
                "    <table border=\"1\">\n" +
                "        <thead>\n" +
                "            <tr>\n" +
                "                <th>Header 1</th>\n" +
                "                <th>Header 2</th>\n" +
                "                <th>Header 3</th>\n" +
                "            </tr>\n" +
                "        </thead>\n" +
                "        <tbody>\n" +
                "            <tr>\n" +
                "                <td>Row 1, Cell 1</td>\n" +
                "                <td>Row 1, Cell 2</td>\n" +
                "                <td>Row 1, Cell 3</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "                <td>Row 2, Cell 1</td>\n" +
                "                <td>Row 2, Cell 2</td>\n" +
                "                <td>Row 2, Cell 3</td>\n" +
                "            </tr>\n" +
                "        </tbody>\n" +
                "    </table>\n" +
                "\n" +
                "    <!-- 表单 -->\n" +
                "    <h2>Forms</h2>\n" +
                "    <form action=\"#\">\n" +
                "        <label for=\"name\">Name:</label>\n" +
                "        <input type=\"text\" id=\"name\" name=\"name\"><br><br>\n" +
                "\n" +
                "        <label for=\"email\">Email:</label>\n" +
                "        <input type=\"email\" id=\"email\" name=\"email\"><br><br>\n" +
                "\n" +
                "        <label for=\"password\">Password:</label>\n" +
                "        <input type=\"password\" id=\"password\" name=\"password\"><br><br>\n" +
                "\n" +
                "        <label for=\"file\">File:</label>\n" +
                "        <input type=\"file\" id=\"file\" name=\"file\"><br><br>\n" +
                "\n" +
                "        <input type=\"submit\" value=\"Submit\">\n" +
                "    </form>\n" +
                "\n" +
                "    <!-- 媒体元素 -->\n" +
                "    <h2>Media Elements</h2>\n" +
                "    <audio controls>\n" +
                "        <source src=\"audiofile.mp3\" type=\"audio/mpeg\">\n" +
                "        Your browser does not support the audio element.\n" +
                "    </audio>\n" +
                "    <br>\n" +
                "    <video width=\"320\" height=\"240\" controls>\n" +
                "        <source src=\"videofile.mp4\" type=\"video/mp4\">\n" +
                "        Your browser does not support the video tag.\n" +
                "    </video>\n" +
                "\n" +
                "    <!-- 其他元素 -->\n" +
                "    <h2>Other Elements</h2>\n" +
                "    <hr>\n" +
                "    <br>\n" +
                "    <button>Click Me!</button>\n" +
                "    <progress value=\"50\" max=\"100\"></progress>\n" +
                "\n" +
                "</body>\n" +
                "</html>\n";
        html = "|&;$%@'\'\"<>()+\r\n,\\";
        System.err.println(policy.sanitize(html));
    }
}
