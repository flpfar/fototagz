package br.edu.ifspsaocarlos.sdm.fototagz;

/*
* Activity: ImageEditActivity
* Displays an image selected by the user, from camera or gallery, and creates tags on the image where user touched.
* When touching somewhere in image, alert dialog is displayed and if user really wants to create a tag there, he will be
* redirected to the NewTagActivity.
* Opened from: MainActivity
* Opens: NewTagActivity
* */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import br.edu.ifspsaocarlos.sdm.fototagz.model.Tag;
import br.edu.ifspsaocarlos.sdm.fototagz.model.TaggedImage;
import br.edu.ifspsaocarlos.sdm.fototagz.model.db.RealmManager;
import br.edu.ifspsaocarlos.sdm.fototagz.util.Constant;

public class ImageEditActivity extends Activity {

    static final int CAMERA_REQUEST = 1;
    static final int GALLERY_REQUEST = 2;

    static final int TAG_IMAGE_SIZE = 20;

    private ImageView ivImage;
    private RelativeLayout rl;
    private TaggedImage taggedImage = null;

    private String mCurrentPhotoPath;
    private String imageUri;
    private boolean existingTag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_edit);
        RealmManager.open();

        ivImage = (ImageView) findViewById(R.id.iv_image);
        rl = (RelativeLayout) findViewById(R.id.rl_imagetag);

        if(getIntent().hasExtra(Constant.CAME_FROM)){
            int imageFrom = getIntent().getIntExtra(Constant.CAME_FROM, 0);

            switch (imageFrom){
                //TODO: I DON'T NEED FILE PROVIDER!! ERRR
                case Constant.IMAGE_FROM_CAMERA:
                    //intent to open camera to get the image
                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    //if that checks if there is some camera app in the device
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        // create the File where the photo should go
                        File photoFile = null;
                        try {
                            photoFile = createImageFile();
                        } catch (IOException ex) {
                            // error occurred while creating the File
                            Toast.makeText(this, "Could not load photo", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        // Continue only if the File was successfully created
                        if (photoFile != null) {
                            //gets the realUri to access the file (content://...)
                            Uri photoURI = FileProvider.getUriForFile(this,
                                    "br.edu.ifspsaocarlos.sdm.fototagz.fileprovider",
                                    photoFile);
                            imageUri = photoURI.toString();
                            Log.d("CAMERAFILEPROVIDERURI", imageUri);
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                            startActivityForResult(takePictureIntent, CAMERA_REQUEST);
                        }
                    }
                    break;

                case Constant.IMAGE_FROM_GALLERY:
                    //intent to open gallery to get the image
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");

                    // create the File where the photo should go
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                    } catch (IOException ex) {
                        // error occurred while creating the File
                        Toast.makeText(this, "Could not load photo", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        //gets the realUri to access the file (content://...)
                        Uri photoURI = FileProvider.getUriForFile(this,
                                "br.edu.ifspsaocarlos.sdm.fototagz.fileprovider",
                                photoFile);
                        imageUri = photoURI.toString();
                        startActivityForResult(photoPickerIntent, GALLERY_REQUEST);
                    }

                    break;

                case Constant.IMAGE_FROM_FOTOTAGZ_GALLERY:
                    taggedImage = getIntent().getParcelableExtra(Constant.TAGGED_IMAGE);
                    existingTag = true;
                    try {
                        Glide.with(this)
                                .load(taggedImage.getImageUri())
                                .into(ivImage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    for (Tag t : taggedImage.getTags()) {
                        createNewImageTag(ivImage, t.getX(), t.getY());
                    }

                    break;

                default:
                    //closes activity
                    finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode){
            case RESULT_OK:
                //*********RESPONSE FROM GALLERY OR CAMERA*********
                if (requestCode == CAMERA_REQUEST || requestCode == GALLERY_REQUEST) {

                  //if the image comes from gallery, make a copy of it in case user deletes image from phone gallery
                    if(requestCode == GALLERY_REQUEST) {
                        if(data.getData() != null) {
                            final Uri galleryPhotoURI = data.getData();

                            File photoFile = new File(mCurrentPhotoPath);

                            try {
                                InputStream inputStream = null;
                                inputStream = getContentResolver().openInputStream(galleryPhotoURI);
                                FileOutputStream fileOutputStream = null;
                                fileOutputStream = new FileOutputStream(photoFile);
                                copyStream(inputStream, fileOutputStream);
                                fileOutputStream.close();
                                inputStream.close();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    //show chosen image using glide library
                    try {
                        Glide.with(ImageEditActivity.this)
                                .load(imageUri)
                                .into(ivImage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //creates the taggedImage object
                    taggedImage = new TaggedImage(imageUri);

                    //saves taggedImage to BD
                    RealmManager.createTaggedImageDAO().saveTaggedImage(taggedImage);

                    //when user clicks somewhere in imageview, creates a new tag where was touched.
                    ivImage.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(final View v, MotionEvent event) {
                            createNewImageTag(v, (int)event.getX(), (int)event.getY());
                            return false;
                        }
                    });

                //************* RESPONSE FROM NEWTAGACTIVITY *******************
                } else if(requestCode == Constant.NEW_TAG){
                    //response came from NewTagActivity
                    Tag newTag = (Tag) data.getParcelableExtra(Constant.CREATED_TAG);
                    Toast.makeText(this, newTag.getTitle(), Toast.LENGTH_SHORT).show();

                    //TODO: Insert created tag in TaggedImaged tags list
                    taggedImage.addTag(newTag);

                } else {
                    //closes activity
                    finish();
                }
                break;

            case RESULT_CANCELED:
                if(requestCode == Constant.NEW_TAG) {
                    //result came from NewTagActivity -> user pressed "Cancel"
                    int id = data.getIntExtra(Constant.TAG_VIEWID, 0);
                    ImageView ivTag = (ImageView) findViewById(id);
                    ((ViewGroup) ivTag.getParent()).removeView(ivTag);
                } else {
                    finish();
                }
                break;

            default:
                finish();
                break;
        }
    }

    //copy data from gallery to apps memory
    public static void copyStream(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    //creates a new "tag" imageview and show it
    private void createNewImageTag(View v, int x, int y){
        ImageView iv = new ImageView(v.getContext());
        iv.setImageResource(R.drawable.target);
        iv.setLayoutParams(new android.view.ViewGroup.LayoutParams(TAG_IMAGE_SIZE * 2,TAG_IMAGE_SIZE * 2));
        iv.setMaxHeight(TAG_IMAGE_SIZE * 2);
        iv.setMaxWidth(TAG_IMAGE_SIZE * 2);
        iv.setX(x-TAG_IMAGE_SIZE);
        iv.setY(y-TAG_IMAGE_SIZE);
        final int generatedId = View.generateViewId();
        iv.setId(generatedId);
        iv.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                Toast.makeText(v.getContext(), "TESTE "+generatedId, Toast.LENGTH_SHORT).show();
            }
        });
        rl.addView(iv);
        if(!existingTag)
            showConfirmation(iv);
    }

    //confirmation if user really wants to create a tag where he touched
    private void showConfirmation(final ImageView iv){
        new AlertDialog
                .Builder(ImageEditActivity.this)
                .setTitle("Title")
                .setMessage(getResources().getString(R.string.tag_confirmation))
                .setIcon(android.R.drawable.ic_dialog_alert)

                //user chooses 'yes', must show an activity to put details about the point touched
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        //intent to open activity to set information to the point tagged
                        Intent newTagActivity = new Intent(getBaseContext(), NewTagActivity.class);
                        newTagActivity.putExtra(Constant.COORDX, (int)iv.getX() + TAG_IMAGE_SIZE);
                        newTagActivity.putExtra(Constant.COORDY, (int)iv.getY()+TAG_IMAGE_SIZE);
                        newTagActivity.putExtra(Constant.TAG_VIEWID, iv.getId());
                        newTagActivity.putExtra(Constant.IMG_URI, imageUri);
                        startActivityForResult(newTagActivity, Constant.NEW_TAG);
                    }})

                //user chooses 'no'
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        //removes tag imageview from image
//                        ImageView namebar = (ImageView) findViewById(tagId);
                        ((ViewGroup) iv.getParent()).removeView(iv);

                    }}).show();
    }


    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        //This mCurrentPhotoPath will get the following path
        // /storage/emulated/0/Android/data/br.edu.ifspsaocarlos.sdm.fototagz/files/Pictures/JPEG_20180528 (...)
        return image;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RealmManager.close();
    }

}
