// badge_generator.js
const { program } = require('commander');
const QRCode = require('qrcode');
const sharp = require('sharp');
const fs = require('fs');
const csv = require('csv-parser');

class BadgeGenerator {
    constructor(options) {
        this.data = options.data;
        this.name = options.name || options.data;
        this.title = options.title || '';
        this.company = options.company || '';
        this.size = options.size || 200;
        this.color = options.color || '#000000';
        this.logoPath = options.logo;
        this.template = options.template ? JSON.parse(fs.readFileSync(options.template)) : this.defaultTemplate();
    }

    defaultTemplate() {
        return {
            badgeWidth: 600,
            badgeHeight: 400,
            background: '#FFFFFF',
            nameFontSize: 40,
            titleFontSize: 24,
            companyFontSize: 28,
            qrPosition: [380, 80],
            textPosition: [50, 100],
            textColor: '#000000'
        };
    }

    async createQR() {
        // Generate QR as buffer
        const qrBuffer = await QRCode.toBuffer(this.data, {
            errorCorrectionLevel: 'H',
            width: this.size,
            color: {
                dark: this.color,
                light: '#FFFFFF'
            },
            margin: 2
        });
        let img = sharp(qrBuffer);
        // Embed logo if provided
        if (this.logoPath) {
            const logo = sharp(this.logoPath);
            const logoSize = Math.round(this.size * 0.25);
            const logoBuffer = await logo.resize(logoSize, logoSize).toBuffer();
            // Overlay logo on QR
            const overlay = await sharp({
                create: {
                    width: this.size,
                    height: this.size,
                    channels: 4,
                    background: { r: 0, g: 0, b: 0, alpha: 0 }
                }
            }).composite([{ input: logoBuffer, gravity: 'centre' }]).png().toBuffer();
            img = sharp(qrBuffer).composite([{ input: overlay }]);
        }
        return img.toBuffer();
    }

    async generate(outputPath) {
        const template = this.template;
        // Create badge background
        const badgeWidth = template.badgeWidth;
        const badgeHeight = template.badgeHeight;
        // We'll use sharp to build the badge
        let badge = sharp({
            create: {
                width: badgeWidth,
                height: badgeHeight,
                channels: 3,
                background: template.background
            }
        });

        // Create text layers using SVG
        let svgText = `<svg width="${badgeWidth}" height="${badgeHeight}">`;
        const textColor = template.textColor;
        const [textX, textY] = template.textPosition;
        svgText += `<text x="${textX}" y="${textY}" font-size="${template.nameFontSize}" fill="${textColor}" font-family="Arial">${this.name}</text>`;
        if (this.title) {
            svgText += `<text x="${textX}" y="${textY + template.nameFontSize + 10}" font-size="${template.titleFontSize}" fill="${textColor}" font-family="Arial">${this.title}</text>`;
        }
        if (this.company) {
            const yOff = template.nameFontSize + template.titleFontSize + 20;
            svgText += `<text x="${textX}" y="${textY + yOff}" font-size="${template.companyFontSize}" fill="${textColor}" font-family="Arial">${this.company}</text>`;
        }
        svgText += `</svg>`;

        // Generate QR buffer
        const qrBuffer = await this.createQR();

        // Combine: overlay QR and text onto badge
        const [qrX, qrY] = template.qrPosition;
        const qrImage = await sharp(qrBuffer).resize(this.size, this.size).toBuffer();

        badge = badge.composite([
            { input: Buffer.from(svgText), top: 0, left: 0 },
            { input: qrImage, top: qrY, left: qrX }
        ]);

        // Save
        await badge.toFile(outputPath);
        console.log(`Badge saved to ${outputPath}`);
    }
}

program
    .requiredOption('-d, --data <text>', 'Data for QR code')
    .option('-n, --name <text>', 'Name on badge')
    .option('-t, --title <text>', 'Title')
    .option('-c, --company <text>', 'Company')
    .option('-o, --output <file>', 'Output file', 'badge.png')
    .option('-s, --size <number>', 'QR size', parseInt, 200)
    .option('--color <color>', 'QR color', '#000000')
    .option('--logo <path>', 'Logo path')
    .option('--template <path>', 'Template JSON file')
    .option('--batch <csv>', 'CSV for batch generation')
    .option('--output-dir <dir>', 'Output directory for batch', '.')
    .parse(process.argv);

const opts = program.opts();

if (opts.batch) {
    // Batch mode
    const results = [];
    fs.createReadStream(opts.batch)
        .pipe(csv())
        .on('data', (row) => results.push(row))
        .on('end', async () => {
            for (const row of results) {
                const data = row.data || '';
                const name = row.name || data;
                const title = row.title || '';
                const company = row.company || '';
                const outName = row.output || `${name.replace(/\s/g, '_')}.png`;
                const outPath = `${opts.outputDir}/${outName}`;
                const gen = new BadgeGenerator({
                    data, name, title, company,
                    size: opts.size,
                    color: opts.color,
                    logo: opts.logo,
                    template: opts.template
                });
                await gen.generate(outPath);
            }
        });
} else {
    const gen = new BadgeGenerator({
        data: opts.data,
        name: opts.name || opts.data,
        title: opts.title,
        company: opts.company,
        size: opts.size,
        color: opts.color,
        logo: opts.logo,
        template: opts.template
    });
    gen.generate(opts.output).catch(console.error);
}
