package tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

public class IcoConverter {

    // Les 4 tailles standard pour les fichiers ICO Windows
    private static final int[] ICON_SIZES = { 16, 32, 48, 256 };

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java IcoConverter <input_image_path> <output_ico_path>");
            return;
        }
        try {
            // Charger votre image source (PNG, JPG, etc.)
            BufferedImage image = ImageIO.read(new File(args[0]));

            // Créer le fichier ICO avec les 4 tailles
            File icoFile = new File(args[1]);
            writeMultiSizeIco(image, icoFile, ICON_SIZES);

            System.out.println("ICO file with " + ICON_SIZES.length + " sizes created successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Écrit un BufferedImage dans un fichier ICO avec plusieurs tailles
     */
    public static void writeMultiSizeIco(BufferedImage sourceImage, File output, int[] sizes) throws IOException {
        // Préparer toutes les images redimensionnées
        BufferedImage[] images = new BufferedImage[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            images[i] = resizeImage(sourceImage, sizes[i], sizes[i]);
            images[i] = convertToARGB(images[i]);
        }

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(output))) {
            // Écrire l'en-tête ICO
            writeIcoHeader(dos, images.length);

            // Calculer les offsets pour chaque image
            int offset = 6 + (16 * images.length); // 6 bytes header + 16 bytes per directory entry

            // Écrire toutes les entrées de répertoire
            ByteArrayOutputStream[] imageDataArray = new ByteArrayOutputStream[images.length];
            for (int i = 0; i < images.length; i++) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream tempDos = new DataOutputStream(baos);
                writeIconData(tempDos, images[i]);
                imageDataArray[i] = baos;

                writeIconDirEntry(dos, images[i], imageDataArray[i].size(), offset);
                offset += imageDataArray[i].size();
            }

            // Écrire toutes les données d'image
            for (ByteArrayOutputStream baos : imageDataArray) {
                dos.write(baos.toByteArray());
            }
        }
    }

    /**
     * Redimensionne une image avec une qualité optimale
     */
    private static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Convertit une image en ARGB
     */
    private static BufferedImage convertToARGB(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage argbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argbImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return argbImage;
    }

    /**
     * Écrit l'en-tête du fichier ICO
     */
    private static void writeIcoHeader(DataOutputStream dos, int numImages) throws IOException {
        dos.writeShort(0); // Réservé (doit être 0)
        dos.writeShort(1); // Type (1 = ICO, 2 = CUR)
        dos.writeShort(numImages); // Nombre d'images
    }

    /**
     * Écrit l'entrée de répertoire pour une image
     */
    private static void writeIconDirEntry(DataOutputStream dos, BufferedImage image, int dataSize, int offset)
            throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        dos.writeByte(width == 256 ? 0 : width); // Largeur (0 = 256)
        dos.writeByte(height == 256 ? 0 : height); // Hauteur (0 = 256)
        dos.writeByte(0); // Nombre de couleurs dans la palette (0 = pas de palette)
        dos.writeByte(0); // Réservé
        dos.writeShort(1); // Nombre de plans de couleur
        dos.writeShort(32); // Bits par pixel
        dos.writeInt(dataSize); // Taille des données de l'image
        dos.writeInt(offset); // Offset des données de l'image
    }

    /**
     * Écrit les données de l'image (format BMP)
     */
    private static void writeIconData(DataOutputStream dos, BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        // En-tête BMP (BITMAPINFOHEADER)
        dos.writeInt(40); // Taille de l'en-tête
        dos.writeInt(width); // Largeur
        dos.writeInt(height * 2); // Hauteur * 2 (image + masque)
        dos.writeShort(1); // Nombre de plans
        dos.writeShort(32); // Bits par pixel
        dos.writeInt(0); // Compression (0 = aucune)
        dos.writeInt(0); // Taille de l'image (0 = non compressée)
        dos.writeInt(0); // Résolution horizontale
        dos.writeInt(0); // Résolution verticale
        dos.writeInt(0); // Nombre de couleurs
        dos.writeInt(0); // Nombre de couleurs importantes

        // Écrire les pixels (de bas en haut, format BMP)
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                dos.writeByte((argb) & 0xFF); // Bleu
                dos.writeByte((argb >> 8) & 0xFF); // Vert
                dos.writeByte((argb >> 16) & 0xFF); // Rouge
                dos.writeByte((argb >> 24) & 0xFF); // Alpha
            }
        }

        // Écrire le masque AND (tous les bits à 0 car nous utilisons le canal alpha)
        int maskSize = ((width + 31) / 32) * 4 * height;
        for (int i = 0; i < maskSize; i++) {
            dos.writeByte(0);
        }
    }
}
