// badge_generator.go
package main

import (
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"io"
	"os"
	"strconv"
	"strings"

	"github.com/fogleman/gg"
	"github.com/skip2/go-qrcode"
)

type Template struct {
	BadgeWidth      int    `json:"badge_width"`
	BadgeHeight     int    `json:"badge_height"`
	Background      string `json:"background"`
	NameFontSize    int    `json:"name_font_size"`
	TitleFontSize   int    `json:"title_font_size"`
	CompanyFontSize int    `json:"company_font_size"`
	QRPosition      [2]int `json:"qr_position"`
	TextPosition    [2]int `json:"text_position"`
	TextColor       string `json:"text_color"`
}

type Generator struct {
	Data     string
	Name     string
	Title    string
	Company  string
	Size     int
	Color    string
	LogoPath string
	Template Template
}

func defaultTemplate() Template {
	return Template{
		BadgeWidth:      600,
		BadgeHeight:     400,
		Background:      "#FFFFFF",
		NameFontSize:    40,
		TitleFontSize:   24,
		CompanyFontSize: 28,
		QRPosition:      [2]int{380, 80},
		TextPosition:    [2]int{50, 100},
		TextColor:       "#000000",
	}
}

func loadTemplate(path string) (Template, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Template{}, err
	}
	var t Template
	err = json.Unmarshal(data, &t)
	return t, err
}

func hexToColor(hex string) (color.RGBA, error) {
	hex = strings.TrimPrefix(hex, "#")
	if len(hex) != 6 {
		return color.RGBA{}, fmt.Errorf("invalid hex")
	}
	r, _ := strconv.ParseUint(hex[0:2], 16, 8)
	g, _ := strconv.ParseUint(hex[2:4], 16, 8)
	b, _ := strconv.ParseUint(hex[4:6], 16, 8)
	return color.RGBA{uint8(r), uint8(g), uint8(b), 255}, nil
}

func (g *Generator) createQR() (image.Image, error) {
	qr, err := qrcode.New(g.Data, qrcode.Highest)
	if err != nil {
		return nil, err
	}
	qr.DisableBorder = true
	qrImg := qr.Image(g.Size)
	// If logo provided, embed it (simplified)
	if g.LogoPath != "" {
		// For simplicity, we skip embedding logo in go example
	}
	return qrImg, nil
}

func (g *Generator) Generate(outputPath string) error {
	tmpl := g.Template
	// Create canvas
	dc := gg.NewContext(tmpl.BadgeWidth, tmpl.BadgeHeight)
	// Background
	bgColor, _ := hexToColor(tmpl.Background)
	dc.SetColor(bgColor)
	dc.Clear()
	// Text color
	txtColor, _ := hexToColor(tmpl.TextColor)
	dc.SetColor(txtColor)

	// Draw name
	if err := dc.LoadFontFace("arial.ttf", float64(tmpl.NameFontSize)); err != nil {
		dc.LoadFontFace("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", float64(tmpl.NameFontSize))
	}
	x, y := tmpl.TextPosition[0], tmpl.TextPosition[1]
	dc.DrawString(g.Name, float64(x), float64(y))
	if g.Title != "" {
		dc.SetFontSize(float64(tmpl.TitleFontSize))
		dc.DrawString(g.Title, float64(x), float64(y+tmpl.NameFontSize+10))
	}
	if g.Company != "" {
		dc.SetFontSize(float64(tmpl.CompanyFontSize))
		dc.DrawString(g.Company, float64(x), float64(y+tmpl.NameFontSize+tmpl.TitleFontSize+20))
	}

	// Generate and draw QR
	qrImg, err := g.createQR()
	if err != nil {
		return err
	}
	qx, qy := tmpl.QRPosition[0], tmpl.QRPosition[1]
	// Draw QR onto context (gg doesn't support draw.Image directly, use raster)
	// We'll convert to image.RGBA and draw using draw.Draw
	// Alternative: use gg.DrawImage
	dc.DrawImage(qrImg, qx, qy)

	// Save
	return dc.SavePNG(outputPath)
}

func main() {
	var (
		data      string
		name      string
		title     string
		company   string
		output    string
		size      int
		color     string
		logo      string
		template  string
		batch     string
		outputDir string
	)
	flag.StringVar(&data, "data", "", "Data for QR code")
	flag.StringVar(&name, "name", "", "Name on badge")
	flag.StringVar(&title, "title", "", "Title")
	flag.StringVar(&company, "company", "", "Company")
	flag.StringVar(&output, "output", "badge.png", "Output file")
	flag.IntVar(&size, "size", 200, "QR size")
	flag.StringVar(&color, "color", "#000000", "QR color")
	flag.StringVar(&logo, "logo", "", "Logo path")
	flag.StringVar(&template, "template", "", "Template JSON")
	flag.StringVar(&batch, "batch", "", "CSV for batch")
	flag.StringVar(&outputDir, "output-dir", ".", "Output directory")
	flag.Parse()

	if data == "" && batch == "" {
		fmt.Fprintln(os.Stderr, "Error: --data or --batch required")
		os.Exit(1)
	}

	var tmpl Template
	var err error
	if template != "" {
		tmpl, err = loadTemplate(template)
	} else {
		tmpl = defaultTemplate()
	}
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error loading template: %v\n", err)
		os.Exit(1)
	}

	if batch != "" {
		file, err := os.Open(batch)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error opening CSV: %v\n", err)
			os.Exit(1)
		}
		defer file.Close()
		reader := csv.NewReader(file)
		header, err := reader.Read()
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error reading CSV header: %v\n", err)
			os.Exit(1)
		}
		// expect columns: data, name, title, company, output
		for {
			record, err := reader.Read()
			if err == io.EOF {
				break
			}
			if err != nil {
				fmt.Fprintf(os.Stderr, "Error reading CSV record: %v\n", err)
				continue
			}
			row := make(map[string]string)
			for i, col := range header {
				if i < len(record) {
					row[col] = record[i]
				}
			}
			d := row["data"]
			n := row["name"]
			if n == "" {
				n = d
			}
			t := row["title"]
			c := row["company"]
			out := row["output"]
			if out == "" {
				out = n + ".png"
			}
			outPath := outputDir + "/" + out
			gen := Generator{
				Data:     d,
				Name:     n,
				Title:    t,
				Company:  c,
				Size:     size,
				Color:    color,
				LogoPath: logo,
				Template: tmpl,
			}
			if err := gen.Generate(outPath); err != nil {
				fmt.Fprintf(os.Stderr, "Error generating %s: %v\n", outPath, err)
			} else {
				fmt.Printf("Generated %s\n", outPath)
			}
		}
	} else {
		if name == "" {
			name = data
		}
		gen := Generator{
			Data:     data,
			Name:     name,
			Title:    title,
			Company:  company,
			Size:     size,
			Color:    color,
			LogoPath: logo,
			Template: tmpl,
		}
		if err := gen.Generate(output); err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			os.Exit(1)
		} else {
			fmt.Printf("Badge saved to %s\n", output)
		}
	}
}
