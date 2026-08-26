// badge_generator.rs
use clap::{App, Arg};
use qrcode_generator::QrCodeEcc;
use image::{ImageBuffer, Rgba, ImageFormat};
use imageproc::drawing::draw_text_mut;
use rusttype::{Font, Scale, point};
use std::fs::File;
use std::io::BufReader;
use serde_json::Value;
use std::collections::HashMap;
use csv::ReaderBuilder;

struct Template {
    badge_width: u32,
    badge_height: u32,
    background: [u8; 4],
    name_font_size: f32,
    title_font_size: f32,
    company_font_size: f32,
    qr_position: (u32, u32),
    text_position: (u32, u32),
    text_color: [u8; 4],
}

impl Default for Template {
    fn default() -> Self {
        Template {
            badge_width: 600,
            badge_height: 400,
            background: [255, 255, 255, 255],
            name_font_size: 40.0,
            title_font_size: 24.0,
            company_font_size: 28.0,
            qr_position: (380, 80),
            text_position: (50, 100),
            text_color: [0, 0, 0, 255],
        }
    }
}

fn hex_to_rgba(hex: &str) -> [u8; 4] {
    let hex = hex.trim_start_matches('#');
    if hex.len() == 6 {
        let r = u8::from_str_radix(&hex[0..2], 16).unwrap_or(0);
        let g = u8::from_str_radix(&hex[2..4], 16).unwrap_or(0);
        let b = u8::from_str_radix(&hex[4..6], 16).unwrap_or(0);
        [r, g, b, 255]
    } else {
        [0, 0, 0, 255]
    }
}

fn generate_qr(data: &str, size: u32, color: &str, logo_path: Option<&str>) -> ImageBuffer<Rgba<u8>, Vec<u8>> {
    let qr = qrcode_generator::to_png_to_vec(data, QrCodeEcc::H, size as u32)
        .expect("QR generation failed");
    // Convert from Vec<u8> to ImageBuffer (PNG format)
    let img = image::load_from_memory(&qr).unwrap().to_rgba8();
    // Change color (simple: replace non-transparent pixels with color)
    let target = hex_to_rgba(color);
    let mut img = img;
    for pixel in img.pixels_mut() {
        if pixel[3] > 0 {
            *pixel = image::Rgba(target);
        }
    }
    // Embed logo (simplified: no-op)
    img
}

fn draw_text(img: &mut ImageBuffer<Rgba<u8>, Vec<u8>>, text: &str, x: i32, y: i32, size: f32, color: [u8; 4]) {
    let font_data = include_bytes!("DejaVuSans.ttf"); // In real code, load from file
    let font = Font::try_from_bytes(font_data as &[u8]).unwrap();
    let scale = Scale::uniform(size);
    draw_text_mut(img, Rgba(color), x, y, scale, &font, text);
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let matches = App::new("Badge Generator")
        .arg(Arg::with_name("data").long("data").required_unless("batch").takes_value(true).help("Data for QR"))
        .arg(Arg::with_name("name").long("name").takes_value(true).help("Name"))
        .arg(Arg::with_name("title").long("title").takes_value(true).help("Title"))
        .arg(Arg::with_name("company").long("company").takes_value(true).help("Company"))
        .arg(Arg::with_name("output").long("output").takes_value(true).default_value("badge.png"))
        .arg(Arg::with_name("size").long("size").takes_value(true).default_value("200"))
        .arg(Arg::with_name("color").long("color").takes_value(true).default_value("#000000"))
        .arg(Arg::with_name("logo").long("logo").takes_value(true))
        .arg(Arg::with_name("template").long("template").takes_value(true))
        .arg(Arg::with_name("batch").long("batch").takes_value(true))
        .arg(Arg::with_name("output-dir").long("output-dir").takes_value(true).default_value("."))
        .get_matches();

    let template = if let Some(tmpl_path) = matches.value_of("template") {
        let file = File::open(tmpl_path)?;
        let reader = BufReader::new(file);
        let v: Value = serde_json::from_reader(reader)?;
        // parse into Template (simplified)
        Template {
            badge_width: v["badge_width"].as_u64().unwrap_or(600) as u32,
            badge_height: v["badge_height"].as_u64().unwrap_or(400) as u32,
            background: hex_to_rgba(v["background"].as_str().unwrap_or("#FFFFFF")),
            name_font_size: v["name_font_size"].as_f64().unwrap_or(40.0) as f32,
            title_font_size: v["title_font_size"].as_f64().unwrap_or(24.0) as f32,
            company_font_size: v["company_font_size"].as_f64().unwrap_or(28.0) as f32,
            qr_position: (
                v["qr_position"][0].as_u64().unwrap_or(380) as u32,
                v["qr_position"][1].as_u64().unwrap_or(80) as u32,
            ),
            text_position: (
                v["text_position"][0].as_u64().unwrap_or(50) as u32,
                v["text_position"][1].as_u64().unwrap_or(100) as u32,
            ),
            text_color: hex_to_rgba(v["text_color"].as_str().unwrap_or("#000000")),
        }
    } else {
        Template::default()
    };

    let size: u32 = matches.value_of("size").unwrap().parse()?;
    let color = matches.value_of("color").unwrap();
    let logo = matches.value_of("logo");

    if let Some(batch_path) = matches.value_of("batch") {
        // Batch mode
        let file = File::open(batch_path)?;
        let mut rdr = ReaderBuilder::new().has_headers(true).from_reader(file);
        let headers = rdr.headers()?.clone();
        for result in rdr.records() {
            let record = result?;
            let mut row = HashMap::new();
            for (i, h) in headers.iter().enumerate() {
                row.insert(h.to_string(), record.get(i).unwrap_or("").to_string());
            }
            let d = row.get("data").unwrap_or(&String::new()).clone();
            let n = row.get("name").unwrap_or(&d).clone();
            let t = row.get("title").unwrap_or(&String::new()).clone();
            let c = row.get("company").unwrap_or(&String::new()).clone();
            let out = row.get("output").unwrap_or(&format!("{}.png", n)).clone();
            let out_path = format!("{}/{}", matches.value_of("output-dir").unwrap(), out);
            // Generate
            let qr_img = generate_qr(&d, size, color, logo);
            let mut badge = ImageBuffer::from_pixel(template.badge_width, template.badge_height,
                Rgba(template.background));
            // Draw text (simplified: draw all text)
            let (tx, ty) = template.text_position;
            draw_text(&mut badge, &n, tx as i32, ty as i32, template.name_font_size, template.text_color);
            if !t.is_empty() {
                draw_text(&mut badge, &t, tx as i32, (ty + template.name_font_size as u32 + 10) as i32,
                    template.title_font_size, template.text_color);
            }
            if !c.is_empty() {
                draw_text(&mut badge, &c, tx as i32, (ty + template.name_font_size as u32 + template.title_font_size as u32 + 20) as i32,
                    template.company_font_size, template.text_color);
            }
            // Paste QR
            let (qx, qy) = template.qr_position;
            for y in 0..size {
                for x in 0..size {
                    let pixel = qr_img.get_pixel(x, y);
                    if pixel[3] > 0 {
                        badge.put_pixel(qx + x, qy + y, *pixel);
                    }
                }
            }
            badge.save(&out_path)?;
            println!("Generated {}", out_path);
        }
    } else {
        // Single mode
        let data = matches.value_of("data").unwrap();
        let name = matches.value_of("name").unwrap_or(data);
        let title = matches.value_of("title").unwrap_or("");
        let company = matches.value_of("company").unwrap_or("");
        let output = matches.value_of("output").unwrap();

        let qr_img = generate_qr(data, size, color, logo);
        let mut badge = ImageBuffer::from_pixel(template.badge_width, template.badge_height,
            Rgba(template.background));
        // Draw text
        let (tx, ty) = template.text_position;
        draw_text(&mut badge, name, tx as i32, ty as i32, template.name_font_size, template.text_color);
        if !title.is_empty() {
            draw_text(&mut badge, title, tx as i32, (ty + template.name_font_size as u32 + 10) as i32,
                template.title_font_size, template.text_color);
        }
        if !company.is_empty() {
            draw_text(&mut badge, company, tx as i32, (ty + template.name_font_size as u32 + template.title_font_size as u32 + 20) as i32,
                template.company_font_size, template.text_color);
        }
        // Paste QR
        let (qx, qy) = template.qr_position;
        for y in 0..size {
            for x in 0..size {
                let pixel = qr_img.get_pixel(x, y);
                if pixel[3] > 0 {
                    badge.put_pixel(qx + x, qy + y, *pixel);
                }
            }
        }
        badge.save(output)?;
        println!("Badge saved to {}", output);
    }
    Ok(())
}
