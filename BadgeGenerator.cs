// BadgeGenerator.cs
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using QRCoder;
using CsvHelper;
using CsvHelper.Configuration;

namespace BadgeGenerator
{
    class Template
    {
        public int BadgeWidth { get; set; }
        public int BadgeHeight { get; set; }
        public string Background { get; set; }
        public int NameFontSize { get; set; }
        public int TitleFontSize { get; set; }
        public int CompanyFontSize { get; set; }
        public int[] QRPosition { get; set; }
        public int[] TextPosition { get; set; }
        public string TextColor { get; set; }
    }

    class Program
    {
        static Template DefaultTemplate()
        {
            return new Template
            {
                BadgeWidth = 600,
                BadgeHeight = 400,
                Background = "#FFFFFF",
                NameFontSize = 40,
                TitleFontSize = 24,
                CompanyFontSize = 28,
                QRPosition = new int[] { 380, 80 },
                TextPosition = new int[] { 50, 100 },
                TextColor = "#000000"
            };
        }

        static Template LoadTemplate(string path)
        {
            string json = File.ReadAllText(path);
            return JsonSerializer.Deserialize<Template>(json);
        }

        static Color HexToColor(string hex)
        {
            return ColorTranslator.FromHtml(hex);
        }

        static Bitmap CreateQR(string data, int size, string color, string logoPath)
        {
            QRCodeGenerator qrGenerator = new QRCodeGenerator();
            QRCodeData qrData = qrGenerator.CreateQrCode(data, QRCodeGenerator.ECCLevel.H);
            QRCode qrCode = new QRCode(qrData);
            Bitmap qrBitmap = qrCode.GetGraphic(size, HexToColor(color), Color.White, true);
            if (!string.IsNullOrEmpty(logoPath) && File.Exists(logoPath))
            {
                using (Bitmap logo = new Bitmap(logoPath))
                {
                    int logoSize = size / 4;
                    Bitmap scaledLogo = new Bitmap(logo, new Size(logoSize, logoSize));
                    Graphics g = Graphics.FromImage(qrBitmap);
                    int x = (size - logoSize) / 2;
                    int y = (size - logoSize) / 2;
                    g.DrawImage(scaledLogo, new Point(x, y));
                    g.Dispose();
                }
            }
            return qrBitmap;
        }

        static void GenerateBadge(string data, string name, string title, string company,
                                  string output, int size, string color, string logoPath,
                                  Template template)
        {
            using (Bitmap qr = CreateQR(data, size, color, logoPath))
            using (Bitmap badge = new Bitmap(template.BadgeWidth, template.BadgeHeight))
            {
                Graphics g = Graphics.FromImage(badge);
                g.Clear(HexToColor(template.Background));
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAlias;

                Brush textBrush = new SolidBrush(HexToColor(template.TextColor));
                Font nameFont = new Font("Arial", template.NameFontSize);
                Font titleFont = new Font("Arial", template.TitleFontSize);
                Font companyFont = new Font("Arial", template.CompanyFontSize);

                int tx = template.TextPosition[0];
                int ty = template.TextPosition[1];
                g.DrawString(name, nameFont, textBrush, tx, ty);
                if (!string.IsNullOrEmpty(title))
                {
                    g.DrawString(title, titleFont, textBrush, tx, ty + template.NameFontSize + 10);
                }
                if (!string.IsNullOrEmpty(company))
                {
                    g.DrawString(company, companyFont, textBrush, tx, ty + template.NameFontSize + template.TitleFontSize + 20);
                }

                int qx = template.QRPosition[0];
                int qy = template.QRPosition[1];
                g.DrawImage(qr, qx, qy);

                string ext = Path.GetExtension(output).ToLower();
                if (ext == ".png")
                    badge.Save(output, ImageFormat.Png);
                else
                    badge.Save(output, ImageFormat.Png); // fallback
                Console.WriteLine($"Badge saved to {output}");
            }
        }

        static void Main(string[] args)
        {
            string data = null, name = null, title = null, company = null, output = "badge.png";
            int size = 200;
            string color = "#000000";
            string logo = null;
            string templatePath = null;
            string batch = null;
            string outputDir = ".";

            for (int i = 0; i < args.Length; i++)
            {
                switch (args[i])
                {
                    case "--data": data = args[++i]; break;
                    case "--name": name = args[++i]; break;
                    case "--title": title = args[++i]; break;
                    case "--company": company = args[++i]; break;
                    case "--output": output = args[++i]; break;
                    case "--size": size = int.Parse(args[++i]); break;
                    case "--color": color = args[++i]; break;
                    case "--logo": logo = args[++i]; break;
                    case "--template": templatePath = args[++i]; break;
                    case "--batch": batch = args[++i]; break;
                    case "--output-dir": outputDir = args[++i]; break;
                }
            }

            Template template = templatePath != null ? LoadTemplate(templatePath) : DefaultTemplate();

            if (batch != null)
            {
                using (var reader = new StreamReader(batch))
                using (var csv = new CsvReader(reader, new CsvConfiguration(System.Globalization.CultureInfo.InvariantCulture)))
                {
                    var records = csv.GetRecords<dynamic>();
                    foreach (var record in records)
                    {
                        string d = record.data;
                        string n = string.IsNullOrEmpty(record.name) ? d : record.name;
                        string t = record.title;
                        string c = record.company;
                        string outFile = string.IsNullOrEmpty(record.output) ? n + ".png" : record.output;
                        string outPath = Path.Combine(outputDir, outFile);
                        GenerateBadge(d, n, t, c, outPath, size, color, logo, template);
                    }
                }
            }
            else
            {
                if (string.IsNullOrEmpty(data)) { Console.Error.WriteLine("Error: --data required"); Environment.Exit(1); }
                if (string.IsNullOrEmpty(name)) name = data;
                GenerateBadge(data, name, title, company, output, size, color, logo, template);
            }
        }
    }
}
