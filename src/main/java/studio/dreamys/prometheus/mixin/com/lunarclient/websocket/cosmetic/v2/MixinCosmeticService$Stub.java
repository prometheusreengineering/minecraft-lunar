package studio.dreamys.prometheus.mixin.com.lunarclient.websocket.cosmetic.v2;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.Timestamp;
import com.lunarclient.websocket.cosmetic.v2.CosmeticService;
import com.lunarclient.websocket.cosmetic.v2.CreateOutfitRequest;
import com.lunarclient.websocket.cosmetic.v2.CreateOutfitResponse;
import com.lunarclient.websocket.cosmetic.v2.DeleteOutfitRequest;
import com.lunarclient.websocket.cosmetic.v2.DeleteOutfitResponse;
import com.lunarclient.websocket.cosmetic.v2.LoginRequest;
import com.lunarclient.websocket.cosmetic.v2.LoginResponse;
import com.lunarclient.websocket.cosmetic.v2.Outfit;
import com.lunarclient.websocket.cosmetic.v2.OutfitTree;
import com.lunarclient.websocket.cosmetic.v2.SelectOutfitRequest;
import com.lunarclient.websocket.cosmetic.v2.SelectOutfitResponse;
import com.lunarclient.websocket.cosmetic.v2.UpdateOutfitRequest;
import com.lunarclient.websocket.cosmetic.v2.UpdateOutfitResponse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import studio.dreamys.prometheus.util.FileUtils;

import java.util.List;
import java.util.logging.Level;

import static studio.dreamys.prometheus.util.FileUtils.logger;

@Mixin(value = CosmeticService.Stub.class, remap = false)
public class MixinCosmeticService$Stub {
    @Overwrite
    public void login(RpcController rpcController, LoginRequest loginRequest, RpcCallback<LoginResponse> rpcCallback) {
        logger.log(Level.INFO, "Patched cosmetic service login request.");

        //load saved outfits from file
        List<Outfit> outfits = FileUtils.readOutfits();
        OutfitTree outfitTree = FileUtils.readOutfitTree(outfits);

        //create our own response
        LoginResponse response = LoginResponse.newBuilder()
                .setHasAllCosmeticsFlag(true)
                .setArtistTools(true)
                .addAllOutfits(outfits)
                .setOutfitTree(outfitTree)
                .build();

        //let lunar handle the rest
        rpcCallback.run(response);
    }

    @Overwrite
    public void createOutfit(RpcController rpcController, CreateOutfitRequest createOutfitRequest, RpcCallback<CreateOutfitResponse> rpcCallback) {
        logger.log(Level.INFO, "Patched cosmetic service create outfit request.");

        long now = System.currentTimeMillis();
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(now / 1000)
                .setNanos((int) (now % 1000) * 1000000)
                .build();

        //create our own outfit
        Outfit outfit = Outfit.newBuilder()
                .setId(FileUtils.randomUuid())
                .setName(createOutfitRequest.getName())
                .setCreatedAt(timestamp)
                .setUpdatedAt(timestamp)
                .build();

        //save outfit to file
        List<Outfit> outfits = FileUtils.readOutfits();
        outfits.add(outfit);
        FileUtils.writeOutfits(outfits);

        //create our own response
        CreateOutfitResponse response = CreateOutfitResponse.newBuilder()
                .setStatus(CreateOutfitResponse.Status.STATUS_OK)
                .setOutfit(outfit)
                .build();

        //let lunar handle the rest
        rpcCallback.run(response);
    }

    @Overwrite
    public void updateOutfit(RpcController rpcController, UpdateOutfitRequest updateOutfitRequest, RpcCallback<UpdateOutfitResponse> rpcCallback) {
        logger.log(Level.INFO, "Patched cosmetic service update outfit request.");

        Outfit outfit = updateOutfitRequest.getOutfit();

        //replace the outfit with the same id, or add it if we don't have it
        List<Outfit> outfits = FileUtils.readOutfits();
        boolean replaced = false;
        for (int i = 0; i < outfits.size(); i++) {
            if (outfits.get(i).getId().equals(outfit.getId())) {
                outfits.set(i, outfit);
                replaced = true;
            }
        }
        if (!replaced) {
            outfits.add(outfit);
        }

        //save outfits to file
        FileUtils.writeOutfits(outfits);
    }

    @Overwrite
    public void deleteOutfit(RpcController rpcController, DeleteOutfitRequest deleteOutfitRequest, RpcCallback<DeleteOutfitResponse> rpcCallback) {
        logger.log(Level.INFO, "Patched cosmetic service delete outfit request.");

        //remove outfit from file, along with any references to it
        List<Outfit> outfits = FileUtils.readOutfits();
        OutfitTree outfitTree = FileUtils.readOutfitTree(outfits);
        for (int i = outfits.size() - 1; i >= 0; i--) {
            if (outfits.get(i).getId().equals(deleteOutfitRequest.getOutfitId())) {
                outfits.remove(i);
            }
        }
        FileUtils.writeOutfits(outfits);
        FileUtils.writeOutfitTree(FileUtils.fixOutfitTree(outfitTree, outfits));

        //create our own response
        DeleteOutfitResponse response = DeleteOutfitResponse.newBuilder()
                .setStatus(DeleteOutfitResponse.Status.STATUS_OK)
                .build();

        //let lunar handle the rest
        rpcCallback.run(response);
    }

    @Overwrite
    public void selectOutfit(RpcController rpcController, SelectOutfitRequest selectOutfitRequest, RpcCallback<SelectOutfitResponse> rpcCallback) {
        logger.log(Level.INFO, "Patched cosmetic service select outfit request.");

        //save outfit tree to file
        FileUtils.writeOutfitTree(selectOutfitRequest.getOutfitTree());

        //create our own response
        SelectOutfitResponse response = SelectOutfitResponse.newBuilder()
                .setStatus(SelectOutfitResponse.Status.STATUS_OK)
                .build();

        //let lunar handle the rest
        rpcCallback.run(response);
    }
}
